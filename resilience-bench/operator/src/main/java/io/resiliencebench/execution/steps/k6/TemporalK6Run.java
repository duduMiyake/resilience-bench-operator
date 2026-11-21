package io.resiliencebench.execution.steps.k6;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.vertx.core.json.JsonObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@Component
public class TemporalK6Run {

  private static final int API_PORT = 6565;
  private static final long START_TIMEOUT_NS = Duration.ofMinutes(5).toNanos();
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
  private static final long STOP_TIMEOUT_NS = Duration.ofMinutes(2).toNanos();

  private final KubernetesClient kubernetes;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();

  public TemporalK6Run(KubernetesClient kubernetes) {
    this.kubernetes = kubernetes;
  }

  public void awaitPausedAndRun(Job job, Runnable onStart, Runnable onWorkloadStopped)
          throws IOException, InterruptedException {
    var namespace = job.getMetadata().getNamespace();
    var name = job.getMetadata().getName();
    var uid = job.getMetadata().getUid();
    var deadline = System.nanoTime() + START_TIMEOUT_NS;
    while (System.nanoTime() - deadline < 0) {
      var current = checkedJob(namespace, name, uid);
      if (completed(current)) {
        throw new IllegalStateException("k6 job completed before paused startup");
      }
      var k6Pod = findRunningPod(namespace, name, uid);
      if (k6Pod.isPresent() && runPausedPod(k6Pod.get(), namespace, name, uid, onStart, onWorkloadStopped)) {
        return;
      }
      Thread.sleep(200);
    }
    throw new IllegalStateException("Timed out waiting for k6 to start paused: " + name);
  }

  private Optional<Pod> findRunningPod(String namespace, String name, String jobUid) {
    return kubernetes.pods().inNamespace(namespace).withLabel("job-name", name).list().getItems().stream()
            .filter(pod -> pod.getMetadata().getOwnerReferences().stream()
                    .anyMatch(owner -> jobUid.equals(owner.getUid())))
            .filter(pod -> pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null)
            .filter(pod -> pod.getStatus().getContainerStatuses().stream().anyMatch(container ->
                    "k6".equals(container.getName()) && container.getState() != null
                            && container.getState().getRunning() != null))
            .findFirst();
  }

  private boolean runPausedPod(Pod pod, String namespace, String name, String uid,
                               Runnable onStart, Runnable onWorkloadStopped)
          throws IOException, InterruptedException {
    boolean started = false;
    boolean observedRunning = false;
    Long stoppedAt = null;
    try (var forward = kubernetes.pods().inNamespace(namespace).withName(pod.getMetadata().getName())
            .portForward(API_PORT)) {
      var api = URI.create("http://127.0.0.1:" + forward.getLocalPort() + "/v1/status");
      var status = attributes(send(request(api).GET().build()));
      if (!Boolean.TRUE.equals(status.getBoolean("paused"))) {
        throw new IllegalStateException("k6 was not paused before the temporal scenario");
      }
      started = true;
      onStart.run();
      var resume = request(api).header("Content-Type", "application/json")
              .method("PATCH", HttpRequest.BodyPublishers.ofString(
                      "{\"data\":{\"type\":\"status\",\"id\":\"default\",\"attributes\":{\"paused\":false}}}"))
              .build();
      if (!Boolean.FALSE.equals(attributes(send(resume)).getBoolean("paused"))) {
        throw new IllegalStateException("k6 did not acknowledge resume for " + name);
      }
      long runDeadline = System.nanoTime() + Duration.ofSeconds(K6JobFactory.TEMPORAL_JOB_DEADLINE_SECONDS).toNanos();
      while (System.nanoTime() - runDeadline < 0) {
        if (completed(checkedJob(namespace, name, uid))) {
          if (stoppedAt == null) {
            onWorkloadStopped.run();
          }
          return true;
        }
        if (stoppedAt == null) {
          try {
            var live = attributes(send(request(api).GET().build()));
            var running = live.getBoolean("running");
            observedRunning |= Boolean.TRUE.equals(running);
            if (observedRunning && Boolean.FALSE.equals(running)) {
              stoppedAt = System.nanoTime();
              onWorkloadStopped.run();
            }
          } catch (IOException e) {
            // REST can close before Kubernetes reports the Pod/Job exit. A live
            // Pod with an unavailable control channel is not a clean stop.
            stoppedAt = System.nanoTime();
            onWorkloadStopped.run();
            var deadline = stoppedAt + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() - deadline < 0) {
              if (completed(checkedJob(namespace, name, uid))) {
                return true;
              }
              Thread.sleep(200);
            }
            throw new IllegalStateException("k6 lost its control channel without completing: " + name, e);
          }
        } else if (System.nanoTime() - stoppedAt >= STOP_TIMEOUT_NS) {
          throw new IllegalStateException("k6 job did not finish after the workload stopped: " + name);
        }
        Thread.sleep(200);
      }
      throw new IllegalStateException("Timed out waiting for k6 to complete: " + name);
    } catch (KubernetesClientException | IOException e) {
      if (started) {
        throw e;
      }
      return false; // pod running does not imply the local REST listener is already ready
    }
  }

  private Job checkedJob(String namespace, String name, String uid) {
    var job = kubernetes.batch().v1().jobs().inNamespace(namespace).withName(name).get();
    if (job == null || !uid.equals(job.getMetadata().getUid()) || job.getMetadata().getDeletionTimestamp() != null) {
      throw new IllegalStateException("k6 job removed or replaced: " + name);
    }
    if (job.getStatus() != null && job.getStatus().getConditions().stream().anyMatch(condition ->
            "Failed".equals(condition.getType()) && "True".equals(condition.getStatus()))) {
      throw new IllegalStateException("k6 job failed: " + name);
    }
    return job;
  }

  private boolean completed(Job job) {
    return job.getStatus() != null && job.getStatus().getCompletionTime() != null;
  }

  private HttpRequest.Builder request(URI api) {
    return HttpRequest.newBuilder(api).timeout(REQUEST_TIMEOUT);
  }

  private String send(HttpRequest request) throws IOException, InterruptedException {
    var response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) {
      throw new IOException("k6 status API returned " + response.statusCode());
    }
    return response.body();
  }

  private JsonObject attributes(String response) {
    return new JsonObject(response).getJsonObject("data").getJsonObject("attributes");
  }
}
