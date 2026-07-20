package io.resiliencebench.resources.benchmark;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Nullable;

public class ConnectorTemplate {

  @JsonPropertyDescription("Name of the connector")
  private String name;
  @JsonPropertyDescription("The source service of the connector. This is the service that will be calling the destination service.")
  private ServiceTemplate source;
  @JsonPropertyDescription("The destination service of the connector. This is the service that will be called by the source service.")
  private ServiceTemplate destination;
  @JsonPropertyDescription("Include an additional connector option without resilience configuration for baseline comparison")
  private Boolean includeBaseline;
  @JsonPropertyDescription("Specification of the failure type to be injected in the destination service.")
  @Nullable
  private BenchmarkFaultTemplate fault;
  @Nullable
  @JsonPropertyDescription("The pattern to apply to the connector")
  private PatternTemplate pattern;

  public ConnectorTemplate() {
  }

  public ConnectorTemplate(String name, ServiceTemplate source, ServiceTemplate destination, BenchmarkFaultTemplate fault, PatternTemplate pattern) {
    this(name, source, destination, fault, pattern, null);
  }

  public ConnectorTemplate(String name, ServiceTemplate source, ServiceTemplate destination, BenchmarkFaultTemplate fault,
                           PatternTemplate pattern, Boolean includeBaseline) {
    this.name = name;
    this.source = source;
    this.destination = destination;
    this.fault = fault;
    this.pattern = pattern;
    this.includeBaseline = includeBaseline;
  }

  public String getName() {
    return name;
  }

  public ServiceTemplate getSource() {
    return source;
  }

  public ServiceTemplate getDestination() {
    return destination;
  }

  public Boolean getIncludeBaseline() {
    return includeBaseline;
  }

  public boolean isIncludeBaseline() {
    return Boolean.TRUE.equals(includeBaseline);
  }

  public BenchmarkFaultTemplate getFault() {
    return fault;
  }

  public PatternTemplate getPattern() {
    return pattern;
  }

}
