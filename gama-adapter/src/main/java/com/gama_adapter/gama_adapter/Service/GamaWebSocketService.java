package com.gama_adapter.gama_adapter.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gama_adapter.gama_adapter.dto.GamaStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Service
public class GamaWebSocketService extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(GamaWebSocketService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final KafkaProducerService kafkaProducerService;
    private final GamaDataTransformer gamaDataTransformer;
    private final String gamaWsUrl;
    private final String gamaModelPath;
    private final String gamaExperimentName;

    private WebSocketSession session;
    private String experimentId;
    private String connectionStatus = "DISCONNECTED";
    private String lastMessageTime = "N/A";
    private int messagesForwarded = 0;

    public GamaWebSocketService(
            KafkaProducerService kafkaProducerService,
            GamaDataTransformer gamaDataTransformer,
            @Value("${gama.server.url}") String gamaWsUrl,
            @Value("${gama.model.path}") String gamaModelPath,
            @Value("${gama.experiment.name}") String gamaExperimentName) {
        this.kafkaProducerService = kafkaProducerService;
        this.gamaDataTransformer = gamaDataTransformer;
        this.gamaWsUrl = gamaWsUrl;
        this.gamaModelPath = gamaModelPath;
        this.gamaExperimentName = gamaExperimentName;
    }

    // --- Status accessors for REST monitoring ---
    public String getConnectionStatus() { return connectionStatus; }
    public String getLastMessageTime() { return lastMessageTime; }
    public int getMessagesForwarded() { return messagesForwarded; }
    public String getExperimentId() { return experimentId; }

    @EventListener(ApplicationReadyEvent.class)
    public void connectToGama() {
        logger.info("Connecting to GAMA Server at {} ...", gamaWsUrl);
        connectionStatus = "CONNECTING";
        try {
            WebSocketClient client = new StandardWebSocketClient();
            CompletableFuture<WebSocketSession> future = client.execute(this, gamaWsUrl);

            future.whenComplete((ws, error) -> {
                if (error != null) {
                    connectionStatus = "FAILED";
                    logger.error("❌ FAILED to connect to GAMA Server at {}: {}", gamaWsUrl, error.getMessage());
                    logger.info("💡 Make sure GAMA is open with Server Mode enabled on the correct port.");
                    scheduleReconnect();
                }
            });
        } catch (Exception e) {
            connectionStatus = "FAILED";
            logger.error("❌ Exception connecting to GAMA Server: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.session = session;
        this.connectionStatus = "CONNECTED";
        logger.info("✅ Connected to GAMA Server! Session ID: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        lastMessageTime = Instant.now().toString();

        try {
            JsonNode json = objectMapper.readTree(payload);
            String type = json.has("type") ? json.get("type").asText() : "unknown";

            switch (type) {
                case "ConnectionSuccessful":
                    logger.info("✅ GAMA Server accepted connection. Sending LOAD command...");
                    sendLoadExperiment();
                    break;

                case "CommandExecutedSuccessfully":
                    handleCommandSuccess(json);
                    break;

                case "SimulationStatus":
                    String status = json.get("content").asText();
                    connectionStatus = "SIMULATION_" + status;
                    logger.info("📊 Simulation status: {}", status);
                    if (json.has("exp_id")) {
                        this.experimentId = json.get("exp_id").asText();
                    }
                    break;

                case "SimulationOutput":
                    // Transform raw GAMA output into structured GamaStateEvent
                    GamaStateEvent event = gamaDataTransformer.transform(payload);
                    String structuredJson = gamaDataTransformer.toJson(event);
                    kafkaProducerService.sendMessage(structuredJson);
                    messagesForwarded++;
                    logger.info("📨 SimulationOutput → Kafka (total: {})", messagesForwarded);
                    break;

                default:
                    logger.debug("📩 GAMA [{}]: {}", type,
                        payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);
                    break;
            }
        } catch (Exception e) {
            logger.error("Error parsing GAMA message: {}", e.getMessage());
        }
    }

    private void handleCommandSuccess(JsonNode json) {
        try {
            JsonNode command = json.get("command");
            String commandType = command != null && command.has("type") ? command.get("type").asText() : "unknown";

            if ("load".equals(commandType)) {
                logger.info("✅ Experiment loaded! Sending PLAY command...");
                if (json.has("content")) {
                    this.experimentId = json.get("content").asText();
                }
                sendPlayExperiment();
            } else if ("play".equals(commandType)) {
                connectionStatus = "RUNNING";
                logger.info("🚀 Experiment is now RUNNING! Waiting for simulation data...");
            } else {
                logger.info("Command '{}' executed successfully", commandType);
            }
        } catch (Exception e) {
            logger.error("Error handling command success: {}", e.getMessage());
        }
    }

    private void sendLoadExperiment() {
        String loadCommand = String.format(
                "{\"type\":\"load\",\"model\":\"%s\",\"experiment\":\"%s\"}",
                gamaModelPath, gamaExperimentName);
        sendToGama(loadCommand);
        logger.info("📤 LOAD sent: model={}, experiment={}", gamaModelPath, gamaExperimentName);
    }

    private void sendPlayExperiment() {
        String playCommand = String.format(
                "{\"type\":\"play\",\"exp_id\":\"%s\"}",
                experimentId);
        sendToGama(playCommand);
        logger.info("📤 PLAY sent for experiment {}", experimentId);
    }

    private void sendToGama(String jsonMessage) {
        try {
            if (session != null && session.isOpen()) {
                session.sendMessage(new TextMessage(jsonMessage));
            } else {
                logger.warn("Cannot send: WebSocket session is not open");
            }
        } catch (Exception e) {
            logger.error("Error sending to GAMA: {}", e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        connectionStatus = "ERROR";
        logger.error("❌ Transport error: {}", exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connectionStatus = "DISCONNECTED";
        logger.warn("🔌 Disconnected from GAMA. Status: {}", status);
        this.session = null;
        this.experimentId = null;
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        logger.info("🔄 Reconnecting in 5 seconds...");
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                connectToGama();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
