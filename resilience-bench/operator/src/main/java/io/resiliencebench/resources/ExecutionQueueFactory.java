package io.resiliencebench.resources;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.queue.ExecutionQueueSpec;
import io.resiliencebench.resources.queue.ExecutionQueueItem;
import io.resiliencebench.resources.queue.ExecutionQueueStatus;
import io.resiliencebench.resources.scenario.Scenario;
import static io.resiliencebench.support.Annotations.OWNED_BY;

public class ExecutionQueueFactory {

  public ExecutionQueueFactory() {
    throw new IllegalStateException("Utility class");
  }

  public static ExecutionQueue create(Benchmark benchmark, List<Scenario> scenarios) {
    var meta = new ObjectMetaBuilder()
            .withNamespace(benchmark.getMetadata().getNamespace())
            .addToAnnotations(OWNED_BY, benchmark.getMetadata().getNamespace())
            .withName(benchmark.getMetadata().getName())
            .build();

    var now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));

    var items = scenarios.stream().map(s -> new ExecutionQueueItem(
            s.getMetadata().getName(), createItemResultFile(queueResultFile(now), s.getMetadata().getName()))
    ).toList();
    var spec = new ExecutionQueueSpec(
            queueResultFile(now),
            items,
            benchmark.getMetadata().getName()
    );

    var queue = new ExecutionQueue(spec, meta);
    queue.setStatus(new ExecutionQueueStatus(0, items.size(), 0));
    return queue;
  }

  public static String createItemResultFile(ExecutionQueue queue, String scenarioName) {
    return createItemResultFile(queue.getSpec().getResultFile(), scenarioName);
  }

  private static String queueResultFile(String timestamp) {
    return "/results/%s-results.json".formatted(timestamp);
  }

  private static String createItemResultFile(String resultFile, String scenarioName) {
    if (resultFile.endsWith("-results.json")) {
      return resultFile.replace("-results.json", "-%s.json".formatted(scenarioName));
    }
    return resultFile + "-%s.json".formatted(scenarioName);
  }
}
