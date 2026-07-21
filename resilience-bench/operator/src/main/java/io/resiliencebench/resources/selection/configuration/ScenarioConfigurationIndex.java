package io.resiliencebench.resources.selection.configuration;

import io.resiliencebench.resources.scenario.Scenario;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ScenarioConfigurationIndex {

  private final Map<ResilienceConfigurationKey, List<Scenario>> scenariosByConfiguration;
  private final Map<String, ResilienceConfigurationKey> configurationByScenarioName;

  private ScenarioConfigurationIndex(Map<ResilienceConfigurationKey, List<Scenario>> scenariosByConfiguration,
                                     Map<String, ResilienceConfigurationKey> configurationByScenarioName) {
    this.scenariosByConfiguration = scenariosByConfiguration;
    this.configurationByScenarioName = configurationByScenarioName;
  }

  public static ScenarioConfigurationIndex from(List<Scenario> scenarios) {
    Map<ResilienceConfigurationKey, List<Scenario>> grouped = new LinkedHashMap<>();
    Map<String, ResilienceConfigurationKey> byScenarioName = new LinkedHashMap<>();

    for (Scenario scenario : scenarios) {
      var key = ResilienceConfigurationKey.from(scenario);
      grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(scenario);
      if (scenario.getMetadata() != null && scenario.getMetadata().getName() != null) {
        byScenarioName.put(scenario.getMetadata().getName(), key);
      }
    }

    return new ScenarioConfigurationIndex(grouped, byScenarioName);
  }

  public List<ResilienceConfigurationKey> keys() {
    return List.copyOf(scenariosByConfiguration.keySet());
  }

  public List<Scenario> scenariosFor(ResilienceConfigurationKey key) {
    return scenariosByConfiguration.getOrDefault(key, List.of());
  }

  public List<Scenario> expand(Collection<ResilienceConfigurationKey> keys) {
    return keys.stream()
            .flatMap(key -> scenariosFor(key).stream())
            .toList();
  }

  public Optional<ResilienceConfigurationKey> keyForScenarioName(String scenarioName) {
    return Optional.ofNullable(configurationByScenarioName.get(scenarioName));
  }

  public int totalScenarios() {
    return scenariosByConfiguration.values().stream().mapToInt(List::size).sum();
  }

  public int totalConfigurations() {
    return scenariosByConfiguration.size();
  }

  public Map<ResilienceConfigurationKey, List<Scenario>> asMap() {
    return Map.copyOf(scenariosByConfiguration);
  }
}
