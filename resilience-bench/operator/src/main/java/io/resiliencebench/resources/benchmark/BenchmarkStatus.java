package io.resiliencebench.resources.benchmark;

import io.fabric8.crd.generator.annotation.PrinterColumn;

public class BenchmarkStatus {

  @PrinterColumn(name = "Total Scenarios", priority = 1)
  private int totalScenarios;

  private String message;

  public BenchmarkStatus() {
  }

  public BenchmarkStatus(int totalScenarios) {
    this.totalScenarios = totalScenarios;
  }

  public BenchmarkStatus(int totalScenarios, String message) {
    this.totalScenarios = totalScenarios;
    this.message = message;
  }

  public int getTotalScenarios() {
    return totalScenarios;
  }

  public String getMessage() {
    return message;
  }
}
