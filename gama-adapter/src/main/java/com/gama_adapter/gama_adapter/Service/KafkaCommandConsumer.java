package com.gama_adapter.gama_adapter.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer for the 'gama-commands' topic.
 *
 * Receives commands from the Co-Simulation Engine (orchestrator)
 * and dispatches them to GAMA via the WebSocket connection.
 *
 * Supported commands:
 *   - BLOCK_ROAD       → Block a road segment in the GAMA simulation
 *   - UPDATE_AGENT_STATE → Update an agent's behavior/state
 *   - PAUSE            → Pause the GAMA simulation
 *   - RESUME           → Resume the GAMA simulation
 *   - STOP             → Stop the GAMA simulation
 */
@Service
public class KafkaCommandConsumer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaCommandConsumer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private GamaWebSocketService gamaWebSocket;

    private int commandsReceived = 0;

    @KafkaListener(topics = "${kafka.topic.gama-commands:gama-commands}", groupId = "gama-adapter-commands")
    public void consume(String message) {
        try {
            JsonNode command = objectMapper.readTree(message);
            commandsReceived++;

            String cmdType = command.has("commandType") ? command.get("commandType").asText() : "UNKNOWN";
            String cmdId = command.has("commandId") ? command.get("commandId").asText() : "?";
            String triggeredBy = command.has("triggeredBy") ? command.get("triggeredBy").asText() : "?";

            logger.info("📥 Command received: {} ({}) — triggered by '{}'", cmdType, cmdId, triggeredBy);

            switch (cmdType) {
                case "BLOCK_ROAD" -> handleBlockRoad(command);
                case "UPDATE_AGENT_STATE" -> handleUpdateAgentState(command);
                case "PAUSE" -> handlePause();
                case "RESUME" -> handleResume();
                case "STOP" -> handleStop();
                default -> logger.warn("Unknown command type: {}", cmdType);
            }

        } catch (Exception e) {
            logger.error("Failed to process gama-command: {}", e.getMessage());
            logger.debug("Raw message: {}", message.length() > 500 ? message.substring(0, 500) : message);
        }
    }

    private void handleBlockRoad(JsonNode command) {
        String roadId = command.has("roadId") ? command.get("roadId").asText() : "unknown";
        boolean blocked = !command.has("blocked") || command.get("blocked").asBoolean();

        logger.warn("🚧 BLOCK_ROAD: road '{}' → blocked={}", roadId, blocked);

        // Send command to GAMA via WebSocket
        String gamaCmd = String.format(
            "{\"type\":\"command\",\"action\":\"block_road\",\"road_id\":\"%s\",\"blocked\":%s}",
            roadId, blocked
        );
        gamaWebSocket.sendCommand(gamaCmd);
    }

    private void handleUpdateAgentState(JsonNode command) {
        String agentId = command.has("agentId") ? command.get("agentId").asText() : "unknown";
        String newState = command.has("newState") ? command.get("newState").asText() : "ok";

        logger.info("👤 UPDATE_AGENT_STATE: agent '{}' → state={}", agentId, newState);

        String gamaCmd = String.format(
            "{\"type\":\"command\",\"action\":\"update_agent\",\"agent_id\":\"%s\",\"new_state\":\"%s\"}",
            agentId, newState
        );
        gamaWebSocket.sendCommand(gamaCmd);
    }

    private void handlePause() {
        logger.info("⏸️ PAUSE simulation");
        gamaWebSocket.sendCommand("{\"type\":\"command\",\"action\":\"pause\"}");
    }

    private void handleResume() {
        logger.info("▶️ RESUME simulation");
        gamaWebSocket.sendCommand("{\"type\":\"command\",\"action\":\"resume\"}");
    }

    private void handleStop() {
        logger.info("⏹️ STOP simulation");
        gamaWebSocket.sendCommand("{\"type\":\"command\",\"action\":\"stop\"}");
    }

    public int getCommandsReceived() {
        return commandsReceived;
    }
}
