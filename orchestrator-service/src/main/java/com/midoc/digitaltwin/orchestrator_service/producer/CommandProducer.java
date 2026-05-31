package com.midoc.digitaltwin.orchestrator_service.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.midoc.digitaltwin.orchestrator_service.dto.command.CarlaCommand;
import com.midoc.digitaltwin.orchestrator_service.dto.command.GamaCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka producer for dispatching commands to the CARLA and GAMA adapters.
 *
 * Publishes to:
 *   - carla-commands → Commands for the CARLA adapter (speed limits, traffic lights, etc.)
 *   - gama-commands  → Commands for the GAMA adapter (road blocks, agent updates, etc.)
 */
@Service
public class CommandProducer {

    private static final Logger logger = LoggerFactory.getLogger(CommandProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic.carla-commands}")
    private String carlaCommandsTopic;

    @Value("${kafka.topic.gama-commands}")
    private String gamaCommandsTopic;

    public CommandProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Send a command to the CARLA adapter via Kafka.
     */
    public void sendCarlaCommand(CarlaCommand command) {
        try {
            String json = objectMapper.writeValueAsString(command);
            kafkaTemplate.send(carlaCommandsTopic, command.getCommandId(), json);
            logger.debug("Sent to {}: {}", carlaCommandsTopic, json);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize CarlaCommand: {}", e.getMessage());
        }
    }

    /**
     * Send a command to the GAMA adapter via Kafka.
     */
    public void sendGamaCommand(GamaCommand command) {
        try {
            String json = objectMapper.writeValueAsString(command);
            kafkaTemplate.send(gamaCommandsTopic, command.getCommandId(), json);
            logger.debug("Sent to {}: {}", gamaCommandsTopic, json);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize GamaCommand: {}", e.getMessage());
        }
    }
}
