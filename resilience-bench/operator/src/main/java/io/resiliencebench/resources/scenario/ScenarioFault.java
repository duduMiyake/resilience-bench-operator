package io.resiliencebench.resources.scenario;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.resiliencebench.resources.fault.FaultWindow;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public class ScenarioFault {
  private String provider;
  private int percentage;

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
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

  public ScenarioFault() {
  }

  public ScenarioFault(String provider, int percentage, List<String> services) {
    this(provider, percentage, services, null, null, null, null, null, null);
  }

  public ScenarioFault(String provider, int percentage, List<String> services, String type,
                       Integer status, Integer duration, Integer grpcStatus, List<FaultWindow> windows,
                       Boolean repeat) {
    this.provider = provider;
    this.percentage = percentage;
    this.services = services;
    this.type = type;
    this.status = status;
    this.duration = duration;
    this.grpcStatus = grpcStatus;
    this.windows = windows == null ? null : new ArrayList<>(windows);
    this.repeat = repeat;
  }

  public String getProvider() { return provider; }
  public int getPercentage() { return percentage; }
  public List<String> getServices() { return services; }
  public String getType() { return type; }
  public Integer getStatus() { return status; }
  public Integer getDuration() { return duration; }
  public Integer getGrpcStatus() { return grpcStatus; }
  public List<FaultWindow> getWindows() { return windows; }
  public Boolean getRepeat() { return repeat; }

  public boolean hasWindows() { return windows != null; }

  public void validateWindowProfile() {
    if (!hasWindows()) {
      if (repeat != null || type != null || status != null || duration != null || grpcStatus != null) {
        throw new IllegalArgumentException("Window fault settings require fault.windows");
      }
      return;
    }
    if (windows.isEmpty() || percentage < 0 || percentage > 100
            || services == null || services.isEmpty()
            || services.stream().anyMatch(service -> service == null || service.isBlank())) {
      throw new IllegalArgumentException("Window faults require windows, percentage between 0 and 100, and target services");
    }
    if (windows.stream().anyMatch(window -> window == null || window.start() == null
            || window.start() < 0 || window.duration() == null || window.duration() <= 0)) {
      throw new IllegalArgumentException("Fault windows require start >= 0 and duration > 0");
    }
    if (Boolean.TRUE.equals(repeat) && windows.stream().noneMatch(window -> window.start() > 0)) {
      throw new IllegalArgumentException("Repeating fault windows require at least one healthy interval");
    }
    if ("delay".equals(type)) {
      if (duration == null || duration <= 0 || status != null || grpcStatus != null) {
        throw new IllegalArgumentException("Delay fault requires a positive duration in milliseconds and no abort status");
      }
    } else if ("abort".equals(type)) {
      if ((status == null && grpcStatus == null) || (status != null && (status < 100 || status > 599))
              || (grpcStatus != null && (grpcStatus < 1 || grpcStatus > 16)) || duration != null) {
        throw new IllegalArgumentException("Abort fault requires a valid HTTP or gRPC status and no delay duration");
      }
    } else {
      throw new IllegalArgumentException("Window fault type must be abort or delay");
    }
  }

  public JsonObject windowProfileJson() {
    var json = new JsonObject();
    if (hasWindows()) {
      var schedule = new JsonArray();
      for (var window : windows) {
        schedule.add(JsonObject.of("start", window.start(), "duration", window.duration()));
      }
      json.put("type", type).put("windows", schedule).put("repeat", Boolean.TRUE.equals(repeat));
      if (status != null) json.put("status", status);
      if (duration != null) json.put("duration", duration);
      if (grpcStatus != null) json.put("grpcStatus", grpcStatus);
    }
    return json;
  }

  public JsonObject toJson() {
    var json = new JsonObject()
            .put("fault_provider", provider)
            .put("fault_percentage", percentage)
            .put("fault_services", services);
    if (hasWindows()) json.put("fault_profile", windowProfileJson());
    return json;
  }
}
