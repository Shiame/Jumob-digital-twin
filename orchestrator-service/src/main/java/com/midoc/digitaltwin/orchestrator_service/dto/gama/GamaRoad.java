package com.midoc.digitaltwin.orchestrator_service.dto.gama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a road segment in the GAMA simulation.
 * Carries congestion and traffic flow data.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GamaRoad {
    private String roadId;
    private String osmId;
    private double congestionLevel;
    private double speedCoeff;
    private boolean blocked;
    private java.util.Map<String, String> movingAgents;
}
