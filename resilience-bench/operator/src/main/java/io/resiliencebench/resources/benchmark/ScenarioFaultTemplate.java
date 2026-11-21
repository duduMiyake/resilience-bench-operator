package io.resiliencebench.resources.benchmark;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.resiliencebench.resources.fault.FaultWindow;
import io.resiliencebench.resources.scenario.ScenarioFault;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public class ScenarioFaultTemplate {

  private String provider;

  @JsonInclude(NON_EMPTY)
  private List<Integer> percentages = new ArrayList<>();
  @JsonInclude(NON_EMPTY)
  private List<String> services = new ArrayList<>();

  @JsonInclude(NON_NULL)
  private String type;
  @JsonInclude(NON_NULL)
  private Integer status;
  @JsonInclude(NON_NULL)
  private Integer duration;
  @JsonInclude(NON_NULL)
  private Integer grpcStatus;
  @JsonInclude(NON_NULL)
  @JsonPropertyDescription("Fault windows with relative starts, in milliseconds")
  private List<FaultWindow> windows;
  @JsonInclude(NON_NULL)
  private Boolean repeat;

  public ScenarioFaultTemplate(String provider, List<Integer> percentages, List<String> services) {
    this.provider = provider;
    this.percentages = percentages;
    this.services = services;
  }

  public ScenarioFaultTemplate() {
  }

  public String getProvider() {
    return provider;
  }

  public List<Integer> getPercentages() {
    return percentages;
  }

  public List<String> getServices() {
    return services;
  }

  public String getType() { return type; }
  public Integer getStatus() { return status; }
  public Integer getDuration() { return duration; }
  public Integer getGrpcStatus() { return grpcStatus; }
  public List<FaultWindow> getWindows() { return windows; }
  public Boolean getRepeat() { return repeat; }

  public ScenarioFault forPercentage(int percentage) {
    var fault = new ScenarioFault(provider, percentage, services, type, status, duration, grpcStatus, windows, repeat);
    fault.validateWindowProfile();
    return fault;
  }
}
