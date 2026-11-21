package io.resiliencebench.resources.queue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class ExecutionQueueItem {

  @JsonPropertyDescription("The name of the scenario it belongs to.")
  @JsonProperty(required = true)
  private String scenario;
  @JsonPropertyDescription("The status of the execution. Can be 'pending', 'running', 'finished' or 'failed'. Automatically managed.")
  @JsonProperty(required = true)
  private String status;
  @JsonPropertyDescription("The time when the execution started.")
  private String startedAt;
  @JsonPropertyDescription("The time when the execution finished.")
  private String finishedAt;
  @JsonPropertyDescription("Why a temporal scenario was stopped without a valid result.")
  private String failureReason;
  @JsonPropertyDescription("The path of the file with the item's results. Automatically created.")
  private String resultFile;

  public ExecutionQueueItem(String scenario, String resultFile) {
    this.scenario = scenario;
    this.resultFile = resultFile;
    this.status = Status.PENDING;
    this.finishedAt = "";
    this.startedAt = "";
  }

  public ExecutionQueueItem() {
  }

  public String getScenario() {
    return scenario;
  }

  @JsonIgnore
  public boolean isFinished() {
    return status.equals(Status.FINISHED);
  }

  public void setStatus(String status) {
    this.status = status;
  }

   public String getStatus() {
    return status;
  }

  public void setStartedAt(String startedAt) {
    this.startedAt = startedAt;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public void setFinishedAt(String finishedAt) {
    this.finishedAt = finishedAt;
  }

  @JsonIgnore
  public boolean isPending() {
    return status.equals(Status.PENDING);
  }

  @JsonIgnore
  public boolean isRunning() {
    return status.equals(Status.RUNNING);
  }

  public String getResultFile() {
    return resultFile;
  }

  @JsonIgnore
  public boolean isFailed() {
    return Status.FAILED.equals(status);
  }

  public interface Status {
    String PENDING = "pending";
    String RUNNING = "running";
    String FINISHED = "finished";
    String FAILED = "failed";
  }
}
