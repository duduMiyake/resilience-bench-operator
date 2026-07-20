package io.resiliencebench.execution.resultcache;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScenarioResultCacheBackfillRunnerTest {

  @Test
  void should_skip_backfill_when_disabled() {
    var environment = new MockEnvironment();
    var backfill = mock(ScenarioResultCacheBackfill.class);
    var runner = new ScenarioResultCacheBackfillRunner(environment, backfill);

    runner.run(new DefaultApplicationArguments());

    verify(backfill, never()).importAggregatedResults(any(), any());
  }

  @Test
  void should_run_backfill_when_enabled() {
    var environment = new MockEnvironment()
            .withProperty("RESULT_CACHE_BACKFILL_ENABLED", "true")
            .withProperty("RESULT_CACHE_BACKFILL_BENCHMARK", "hipstershop")
            .withProperty("RESULT_CACHE_BACKFILL_FILE", "/exhaustive/results/run-results.json");
    var backfill = mock(ScenarioResultCacheBackfill.class);
    when(backfill.importAggregatedResults(any(), eq("/exhaustive/results/run-results.json"))).thenReturn(1296);
    var runner = new ScenarioResultCacheBackfillRunner(environment, backfill);

    runner.run(new DefaultApplicationArguments());

    verify(backfill).importAggregatedResults(any(), eq("/exhaustive/results/run-results.json"));
  }

  @Test
  void should_fail_when_required_property_is_missing() {
    var environment = new MockEnvironment()
            .withProperty("RESULT_CACHE_BACKFILL_ENABLED", "true")
            .withProperty("RESULT_CACHE_BACKFILL_BENCHMARK", "hipstershop");
    var runner = new ScenarioResultCacheBackfillRunner(environment, mock(ScenarioResultCacheBackfill.class));

    var exception = assertThrows(IllegalArgumentException.class,
            () -> runner.run(new DefaultApplicationArguments()));

    assertEquals("RESULT_CACHE_BACKFILL_FILE must be set when RESULT_CACHE_BACKFILL_ENABLED=true", exception.getMessage());
  }
}
