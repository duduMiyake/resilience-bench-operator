package io.resiliencebench.resources.fault;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Min;

public record FaultWindow(
        @Min(0) @JsonPropertyDescription("Healthy interval in milliseconds before the window") Long start,
        @Min(1) @JsonPropertyDescription("Duration in milliseconds with the fault active") Long duration) {
}
