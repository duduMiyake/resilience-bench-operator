package io.resiliencebench.resources.benchmark;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class ResultCacheSpec {

  public static final String READ_WRITE = "readWrite";
  public static final String DEFAULT_CACHE_PREFIX = "/results/cache";
  public static final String DEFAULT_RUNS_PREFIX = "/results/runs";

  @JsonPropertyDescription("Enables scenario result cache/replay for this benchmark")
  private Boolean enabled;

  @JsonPropertyDescription("Cache mode. Currently only readWrite is supported")
  private String mode;

  @JsonPropertyDescription("Stable result cache prefix. Defaults to /results/cache")
  private String cachePrefix;

  @JsonPropertyDescription("Execution run artifacts prefix. Defaults to /results/runs")
  private String runsPrefix;

  public ResultCacheSpec() {
  }

  public ResultCacheSpec(Boolean enabled, String mode, String cachePrefix, String runsPrefix) {
    this.enabled = enabled;
    this.mode = mode;
    this.cachePrefix = cachePrefix;
    this.runsPrefix = runsPrefix;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public String getMode() {
    return mode;
  }

  public String getCachePrefix() {
    return cachePrefix;
  }

  public String getRunsPrefix() {
    return runsPrefix;
  }

  public boolean isEnabled() {
    return Boolean.TRUE.equals(enabled);
  }

  public String resolvedMode() {
    return mode == null || mode.isBlank() ? READ_WRITE : mode;
  }

  public String resolvedCachePrefix() {
    return normalizePrefix(cachePrefix, DEFAULT_CACHE_PREFIX);
  }

  public String resolvedRunsPrefix() {
    return normalizePrefix(runsPrefix, DEFAULT_RUNS_PREFIX);
  }

  private static String normalizePrefix(String value, String defaultValue) {
    var prefix = value == null || value.isBlank() ? defaultValue : value.trim();
    while (prefix.endsWith("/") && prefix.length() > 1) {
      prefix = prefix.substring(0, prefix.length() - 1);
    }
    return prefix.startsWith("/") ? prefix : "/" + prefix;
  }
}