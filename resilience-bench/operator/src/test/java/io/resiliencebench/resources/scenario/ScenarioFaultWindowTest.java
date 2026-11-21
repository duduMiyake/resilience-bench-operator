package io.resiliencebench.resources.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.resiliencebench.resources.benchmark.ScenarioFaultTemplate;
import io.resiliencebench.resources.fault.FaultWindow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioFaultWindowTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void preservesTheProfileAcrossBenchmarkExpansionAndScenarioSerialization() throws Exception {
    var template = mapper.readValue("""
            {"provider":"envoy","percentages":[50,100],"services":["payment"],
             "type":"abort","grpcStatus":14,"windows":[{"start":2000,"duration":5000}],"repeat":true}
            """, ScenarioFaultTemplate.class);
    var fault = template.forPercentage(50);
    var roundTrip = mapper.readValue(mapper.writeValueAsString(fault), ScenarioFault.class);
    roundTrip.validateWindowProfile();
    assertEquals(50, roundTrip.getPercentage());
    assertEquals(100, template.forPercentage(100).getPercentage());
    assertEquals(List.of(new FaultWindow(2000L, 5000L)), roundTrip.getWindows());
    assertEquals(fault.windowProfileJson(), roundTrip.windowProfileJson());
    assertTrue(roundTrip.getRepeat());
  }

  @Test
  void rejectsMissingAndInvalidWindowsAndAmbiguousFaults() throws Exception {
    for (var settings : List.of(
            "\"repeat\":false",
            "\"type\":\"abort\",\"grpcStatus\":14,\"windows\":[]",
            "\"type\":\"abort\",\"grpcStatus\":14,\"windows\":[{\"duration\":5}]",
            "\"type\":\"abort\",\"grpcStatus\":14,\"windows\":[{\"start\":0,\"duration\":5}],\"repeat\":true",
            "\"type\":\"delay\",\"status\":503,\"duration\":100,\"windows\":[{\"start\":1,\"duration\":5}]")) {
      var fault = mapper.readValue("{\"percentage\":50,\"services\":[\"payment\"]," + settings + "}", ScenarioFault.class);
      assertThrows(IllegalArgumentException.class, fault::validateWindowProfile, settings);
    }
  }
}
