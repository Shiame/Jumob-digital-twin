package com.midoc.digitaltwin.orchestrator_service.dto.carla;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-level event received from the CARLA Adapter via Kafka topic 'carla-state'.
 * Wraps a CarlaPayload with standard event metadata.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CarlaStateEvent {
    private String eventId;
    private String eventType;   // TICK_COMPLETED
    private String source;      // CARLA
    private long timestamp;
    private CarlaPayload payload;
}
