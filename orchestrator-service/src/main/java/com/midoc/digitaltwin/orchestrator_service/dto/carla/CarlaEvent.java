package com.midoc.digitaltwin.orchestrator_service.dto.carla;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A collision or other event detected by CARLA sensors.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CarlaEvent {
    private String type;           // COLLISION
    private Position position;
    private String otherActorType; // pieton, vehicle, etc.
    private String severity;       // low, medium, high

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Position {
        private double x;
        private double y;
        private Double latitude;
        private Double longitude;
    }
}
