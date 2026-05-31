package com.midoc.digitaltwin.orchestrator_service.dto.gama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a zone in the GAMA simulation with aggregated metrics.
 * Used for pedestrian density and pollution monitoring.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GamaZone {
    private String zoneId;
    private int pedestrianCount;
    private double pollutionLevel;
}
