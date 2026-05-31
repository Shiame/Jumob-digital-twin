package com.midoc.digitaltwin.orchestrator_service.dto.gama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload of a GAMA state event containing simulation data for one tick.
 *
 * Updated to match the full GAMA Adapter output:
 *   - agents: all moving agents (vehicles + pedestrians combined)
 *   - vehicles: vehicle-type agents only
 *   - pedestrians: pedestrian-type agents only
 *   - roads, zones, trafficSignals, busStops, parkings
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GamaPayload {

    /** Simulation cycle number */
    private int cycle;

    @JsonProperty("tickNumber")
    private int tickNumber;

    @JsonProperty("experimentId")
    private String experimentId;

    @JsonProperty("simulationDate")
    private String simulationDate;

    @JsonProperty("nbPeople")
    private int nbPeople;

    /** All moving agents — vehicles + pedestrians combined (use this, not vehicles+pedestrians) */
    private List<GamaAgent> agents = new ArrayList<>();

    /** Vehicle-type agents only (cars, bikes, buses, trucks) */
    private List<GamaAgent> vehicles = new ArrayList<>();

    /** Pedestrian-type agents only */
    private List<GamaAgent> pedestrians = new ArrayList<>();

    /** Per-road congestion and occupation data */
    private List<GamaRoad> roads = new ArrayList<>();

    /** Legacy zone-based data (pedestrian density + pollution) */
    private List<GamaZone> zones = new ArrayList<>();
}
