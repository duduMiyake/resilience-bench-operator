package io.resiliencebench.execution.fault;

import io.resiliencebench.resources.fault.FaultWindow;
import io.resiliencebench.resources.scenario.ScenarioFault;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TemporalFaultSessionTest {

  @Test
  void repeatsRelativeWindowsAndClearsOnCancel() throws Exception {
    var calls = new CopyOnWriteArrayList<Integer>();
    var firstCycle = new CountDownLatch(4);
    var session = new TemporalFaultSession(List.of(new FaultWindow(5L, 5L)), true, 75, value -> {
      calls.add(value);
      firstCycle.countDown();
    }, () -> {});
    try {
      session.start();
      assertTrue(firstCycle.await(5, TimeUnit.SECONDS));
    } finally {
      session.cancel();
    }
    assertEquals(List.of(75, 0, 75, 0), calls.subList(0, 4));
    assertEquals(0, calls.get(calls.size() - 1));
    assertTrue(session.finish().getInteger("faultWindowsCompleted") > 0);
    assertEquals(5L, session.finish().getJsonArray("faultTransitions").getJsonObject(0).getLong("plannedMs"));
    assertEquals(10L, session.finish().getJsonArray("faultTransitions").getJsonObject(1).getLong("plannedMs"));
    assertEquals(15L, session.finish().getJsonArray("faultTransitions").getJsonObject(2).getLong("plannedMs"));
  }

  @Test
  void earlyWorkloadCompletionCannotBecomeAValidResult() throws Exception {
    var calls = new CopyOnWriteArrayList<Integer>();
    var session = new TemporalFaultSession(List.of(new FaultWindow(0L, 600_000L)), false, 100, calls::add, () -> {});
    try {
      session.start();
      assertEquals(List.of(100), calls, "window at zero must activate before start returns");
      assertThrows(IllegalStateException.class, session::finish);
    } finally {
      session.cancel();
    }
    assertEquals(List.of(100, 0), calls);
  }

  @Test
  void unsuccessfulActivationCannotBecomeAValidResult() throws Exception {
    var cleanup = new CountDownLatch(1);
    var session = new TemporalFaultSession(List.of(new FaultWindow(1L, 1L)), false, 100, value -> {
      if (value > 0) {
        throw new IllegalStateException("proxy not reachable");
      }
      cleanup.countDown();
    }, () -> {});
    try {
      session.start();
      assertTrue(cleanup.await(5, TimeUnit.SECONDS));
      var error = assertThrows(IllegalStateException.class, session::finish);
      assertEquals("proxy not reachable", error.getCause().getMessage());
    } finally {
      session.cancel();
    }
  }

  @Test
  void resetFailuresRemainVisibleOnRepeatedCleanupCalls() {
    var released = new java.util.concurrent.atomic.AtomicBoolean();
    var session = new TemporalFaultSession(List.of(new FaultWindow(0L, 1L)), false, 100, value -> {
      throw new IllegalStateException("proxy not reachable");
    }, () -> released.set(true));
    assertThrows(IllegalStateException.class, session::cancel);
    assertTrue(released.get(), "attempt removal even when the zero reset failed");
    assertThrows(IllegalStateException.class, session::finish);
  }

  @Test
  void runtimePreflightRequiresBothZeros() {
    var disabled = new JsonObject().put("final_value", "0");
    var sleeping = new JsonObject().put("entries", new JsonObject()
            .put("fault.http.abort.abort_percent", disabled)
            .put("fault.http.delay.fixed_delay_percent", disabled));
    assertDoesNotThrow(() -> TemporalFaultService.requireDisabledRuntime(sleeping));
    var notDisabled = sleeping.copy();
    notDisabled.getJsonObject("entries").getJsonObject("fault.http.abort.abort_percent").put("final_value", "100");
    assertThrows(IllegalStateException.class, () -> TemporalFaultService.requireDisabledRuntime(notDisabled));
    assertThrows(IllegalStateException.class, () -> TemporalFaultService.requireDisabledRuntime(new JsonObject()));
  }

  @Test
  void runtimeAbortAndDelayAreMutuallyExclusive() {
    var abort = new ScenarioFault("envoy", 50, List.of("paymentservice"), "abort", 503, null, 14, null, null);
    assertEquals(Map.of("fault.http.abort.abort_percent", "50", "fault.http.abort.http_status", "503",
            "fault.http.abort.grpc_status", "14", "fault.http.delay.fixed_delay_percent", "0"),
            TemporalFaultService.runtimeValues(abort, 50));

    var delay = new ScenarioFault("envoy", 80, List.of("paymentservice"), "delay", null, 200, null, null, null);
    assertEquals(Map.of("fault.http.abort.abort_percent", "0", "fault.http.delay.fixed_delay_percent", "80",
            "fault.http.delay.fixed_duration_ms", "200"), TemporalFaultService.runtimeValues(delay, 80));
    assertEquals(Map.of("fault.http.abort.abort_percent", "0", "fault.http.delay.fixed_delay_percent", "0"),
            TemporalFaultService.runtimeValues(delay, 0));
    assertEquals(Map.of("fault.http.abort.abort_percent", "", "fault.http.abort.http_status", "",
            "fault.http.abort.grpc_status", "", "fault.http.delay.fixed_delay_percent", ""),
            TemporalFaultService.clearedOverrides(abort, true));
    assertEquals(Map.of("fault.http.abort.abort_percent", "", "fault.http.delay.fixed_delay_percent", "",
            "fault.http.delay.fixed_duration_ms", ""), TemporalFaultService.clearedOverrides(delay, true));
    assertEquals(Map.of("fault.http.abort.abort_percent", "", "fault.http.delay.fixed_delay_percent", ""),
            TemporalFaultService.clearedOverrides(delay, false));
  }
}
