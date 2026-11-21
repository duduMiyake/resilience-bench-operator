package io.resiliencebench.resources.queue;

import io.fabric8.crd.generator.annotation.PrinterColumn;

public class ExecutionQueueStatus {


  @PrinterColumn(name = "Pending")
  private long pending;
  @PrinterColumn(name = "Running")
  private long running;
  @PrinterColumn(name = "Finished")
  private long finished;
  @PrinterColumn(name = "Failed")
  private long failed;

  public ExecutionQueueStatus() {
  }

  public ExecutionQueueStatus(long running, long pending, long finished) {
    this(running, pending, finished, 0);
  }

  public ExecutionQueueStatus(long running, long pending, long finished, long failed) {
    this.running = running;
    this.pending = pending;
    this.finished = finished;
    this.failed = failed;
  }

  public long getFailed() {
    return failed;
  }

  public long getRunning() {
    return running;
  }

  public long getPending() {
    return pending;
  }

  public long getFinished() {
    return finished;
  }
}
