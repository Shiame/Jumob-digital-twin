package com.midoc.digitaltwin.orchestrator_service.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.midoc.digitaltwin.orchestrator_service.dto.gama.GamaStateEvent;
import com.midoc.digitaltwin.orchestrator_service.engine.CoSimulationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the 'gama-state' topic.
 *
 * Receives GamaStateEvent messages published by the GAMA Adapter,
 * deserializes them, and forwards them to the CoSimulationEngine
 * for rule evaluation.
 */
@Component
public class GamaStateConsumer {

    private static final Logger logger = LoggerFactory.getLogger(GamaStateConsumer.class);

    private final CoSimulationEngine engine;
    private final ObjectMapper objectMapper;

    public GamaStateConsumer(CoSimulationEngine engine) {
        this.engine = engine;
        this.objectMapper = new ObjectMapper();
        logger.info("📡 GamaStateConsumer initialized — listening on topic 'gama-state'");
    }

    @KafkaListener(topics = "${kafka.topic.gama-state}", groupId = "cosimulation-engine")
    public void consume(String message) {
        try {
            GamaStateEvent event = objectMapper.readValue(message, GamaStateEvent.class);

            logger.debug("Received GAMA state — eventId: {} | tick: {}",
                    event.getEventId(),
                    event.getPayload() != null ? event.getPayload().getTickNumber() : "N/A");

            engine.onGamaStateReceived(event);

        } catch (Exception e) {
            logger.error("Failed to deserialize GAMA state message: {}", e.getMessage());
            logger.debug("Raw message: {}",
                    message.length() > 500 ? message.substring(0, 500) + "..." : message);
        }
    }
}
