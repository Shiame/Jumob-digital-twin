package com.midoc.digitaltwin.orchestrator_service.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.midoc.digitaltwin.orchestrator_service.dto.carla.CarlaStateEvent;
import com.midoc.digitaltwin.orchestrator_service.engine.CoSimulationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the 'carla-state' topic.
 *
 * Receives CarlaStateEvent messages published by the CARLA Adapter,
 * deserializes them, and forwards them to the CoSimulationEngine
 * for rule evaluation.
 *
 * Works with both:
 *   - Real CARLA data (from OcciData HPC cluster via generate_traffic.py)
 *   - Mock CARLA data (from MockCarlaConnector for local testing)
 */
@Component
public class CarlaStateConsumer {

    private static final Logger logger = LoggerFactory.getLogger(CarlaStateConsumer.class);

    private final CoSimulationEngine engine;
    private final ObjectMapper objectMapper;

    public CarlaStateConsumer(CoSimulationEngine engine) {
        this.engine = engine;
        this.objectMapper = new ObjectMapper();
        logger.info("📡 CarlaStateConsumer initialized — listening on topic 'carla-state'");
    }

    @KafkaListener(topics = "${kafka.topic.carla-state}", groupId = "cosimulation-engine")
    public void consume(String message) {
        try {
            CarlaStateEvent event = objectMapper.readValue(message, CarlaStateEvent.class);

            logger.debug("Received CARLA state — eventId: {} | tick: {} | vehicles: {}",
                    event.getEventId(),
                    event.getPayload() != null ? event.getPayload().getTickNumber() : "N/A",
                    event.getPayload() != null ? event.getPayload().getNumVehicles() : 0);

            engine.onCarlaStateReceived(event);

        } catch (Exception e) {
            logger.error("Failed to deserialize CARLA state message: {}", e.getMessage());
            logger.debug("Raw message: {}",
                    message.length() > 500 ? message.substring(0, 500) + "..." : message);
        }
    }
}
