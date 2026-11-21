package io.resiliencebench.execution.fault;

import io.resiliencebench.resources.fault.FaultWindow;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/** One run's fault clock. Only acknowledged transitions are counted. */
public final class TemporalFaultSession {

  private static final Logger logger = LoggerFactory.getLogger(TemporalFaultSession.class);

  private final Object signal = new Object();
  private final List<FaultWindow> windows;
  private final boolean repeat;
  private final int percentage;
  private final IntConsumer setPercentage;
  private final Runnable releaseOverrides;
  private final JsonArray transitions = new JsonArray();
  private Thread worker;
  private long startedAt;
  private boolean stopped;
  private RuntimeException failure;
  private RuntimeException cleanupFailure;
  private int windowsStarted;
  private int windowsCompleted;
  private long durationMs;

  TemporalFaultSession(List<FaultWindow> windows, boolean repeat, int percentage, IntConsumer setPercentage,
                       Runnable releaseOverrides) {
    this.windows = List.copyOf(windows);
    this.repeat = repeat;
    this.percentage = percentage;
    this.setPercentage = setPercentage;
    this.releaseOverrides = releaseOverrides;
  }

  public synchronized void start() {
    if (worker != null || stopped) {
      throw new IllegalStateException("Temporal fault session has already started or stopped");
    }
    boolean startsImmediately = windows.get(0).start() == 0;
    if (startsImmediately) {
      // When the workload is still paused, arm its first zero-offset fault before resuming it.
      setPercentage.accept(percentage);
    }
    startedAt = System.nanoTime();
    if (startsImmediately) {
      recordTransition(0, 0, percentage, "start");
      windowsStarted++;
    }
    worker = new Thread(() -> run(startsImmediately), "resiliencebench-fault-timeline");
    worker.setDaemon(true);
    worker.start();
  }

  private void run(boolean skipInitialActivation) {
    long offset = 0;
    try {
      do {
        for (FaultWindow window : windows) {
          offset = Math.addExact(offset, window.start());
          if (!skipInitialActivation) {
            if (!awaitOffset(offset) || !transition(offset, percentage, "start")) {
              return;
            }
            windowsStarted++;
          }
          skipInitialActivation = false;
          offset = Math.addExact(offset, window.duration());
          if (!awaitOffset(offset) || !transition(offset, 0, "end")) {
            return;
          }
          windowsCompleted++;
        }
      } while (repeat);
    } catch (RuntimeException error) {
      failure = error;
      try {
        setPercentage.accept(0);
      } catch (RuntimeException cleanup) {
        error.addSuppressed(cleanup);
      }
      logger.error("Temporal fault transition failed; scenario cannot produce a valid result", error);
    }
  }

  private boolean awaitOffset(long offsetMillis) {
    synchronized (signal) {
      while (!stopped) {
        long remaining = TimeUnit.MILLISECONDS.toNanos(offsetMillis) - (System.nanoTime() - startedAt);
        if (remaining <= 0) {
          return true;
        }
        try {
          TimeUnit.NANOSECONDS.timedWait(signal, remaining);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Temporal fault scheduler interrupted", interrupted);
        }
      }
      return false;
    }
  }

  private boolean transition(long plannedMs, int value, String phase) {
    setPercentage.accept(value);
    synchronized (signal) {
      if (stopped) {
        return false;
      }
      recordTransition(plannedMs, elapsedMs(), value, phase);
      return true;
    }
  }

  private void recordTransition(long plannedMs, long acknowledgedMs, int value, String phase) {
    transitions.add(new JsonObject().put("phase", phase).put("plannedMs", plannedMs)
            .put("acknowledgedMs", acknowledgedMs).put("percentage", value));
    logger.info("Fault window {} at {} ms (scheduled {} ms, {}%)", phase, acknowledgedMs, plannedMs, value);
  }

  private long elapsedMs() {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }

  public synchronized void cancel() {
    if (stopped) {
      if (cleanupFailure != null) {
        throw new IllegalStateException("Previous Envoy reset failed: " + cleanupFailure.getMessage());
      }
      return;
    }
    synchronized (signal) {
      durationMs = worker == null ? 0 : elapsedMs();
      stopped = true;
      signal.notifyAll();
    }
    // Wait for any in-flight write before clearing, so a late ON cannot undo the cleanup.
    boolean interrupted = false;
    try {
      while (worker != null && worker.isAlive()) {
        try {
          worker.join();
        } catch (InterruptedException error) {
          interrupted = true;
        }
      }
      RuntimeException cleanup = null;
      try {
        setPercentage.accept(0);
      } catch (RuntimeException error) {
        cleanup = error;
      }
      try {
        releaseOverrides.run();
      } catch (RuntimeException error) {
        if (cleanup == null) {
          cleanup = error;
        } else if (cleanup != error) {
          cleanup.addSuppressed(error);
        }
      }
      if (cleanup != null) {
        cleanupFailure = cleanup;
        throw cleanup;
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public synchronized JsonObject finish() {
    cancel();
    if (failure != null) {
      throw new IllegalStateException("At least one temporal fault transition was not applied", failure);
    }
    if (windowsCompleted < windows.size()) {
      throw new IllegalStateException("Workload ended before one complete fault cycle: "
              + windowsCompleted + "/" + windows.size() + " windows acknowledged");
    }
    return new JsonObject().put("faultWindowsStarted", windowsStarted)
            .put("faultWindowsCompleted", windowsCompleted)
            .put("scenarioDurationMs", durationMs)
            .put("faultTransitions", transitions.copy());
  }
}
