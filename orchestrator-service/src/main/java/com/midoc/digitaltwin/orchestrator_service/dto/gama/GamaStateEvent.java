package com.midoc.digitaltwin.orchestrator_service.dto.gama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-level event received from the GAMA Adapter via Kafka topic 'gama-state'.
 * Wraps a GamaPayload with standard event metadata.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GamaStateEvent {
    private String eventId;
    private String eventType;   // TICK_COMPLETED
    private String source;      // GAMA
    private long timestamp;
    private GamaPayload payload;
}
