package com.midoc.digitaltwin.orchestrator_service.dto.carla;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload of a CARLA state event containing vehicle telemetry for one tick.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CarlaPayload {

    @JsonProperty("tick_number")
    private int tickNumber;

    @JsonProperty("map_name")
    private String mapName;

    @JsonProperty("num_vehicles")
    private int numVehicles;

    private List<VehicleState> vehicles = new ArrayList<>();

    /** Sensor events (collisions, etc.) — may be absent in some ticks */
    private List<CarlaEvent> events = new ArrayList<>();
}
