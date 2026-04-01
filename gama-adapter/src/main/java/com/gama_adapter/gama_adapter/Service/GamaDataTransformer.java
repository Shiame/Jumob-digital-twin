package com.gama_adapter.gama_adapter.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gama_adapter.gama_adapter.dto.GamaStateEvent;
import com.gama_adapter.gama_adapter.dto.GamaStateEvent.GamaPayload;
import com.gama_adapter.gama_adapter.dto.GamaStateEvent.VehicleData;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforms raw GAMA SimulationOutput into structured GamaStateEvent DTOs.
 */
@Service
public class GamaDataTransformer {

    private static final Logger logger = LoggerFactory.getLogger(GamaDataTransformer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses a raw SimulationOutput message from GAMA and transforms it
     * into a structured GamaStateEvent aligned with kafka_contracts.md.
     *
     * GAMA Server sends messages like:
     * {"type":"SimulationOutput","content":"{\"type\":\"traffic_data\",\"cycle\":10,...}"}
     * OR nested further like:
     * {"type":"SimulationOutput","content":{"message":"{\"type\":\"traffic_data\",...}"}}
     */
    public GamaStateEvent transform(String rawMessage) {
        GamaStateEvent event = new GamaStateEvent();

        try {
            JsonNode root = objectMapper.readTree(rawMessage);
            logger.debug("🔍 Raw root keys: {}", root.fieldNames());

            // Step 1: Extract "content" field
            JsonNode contentNode = root.has("content") ? root.get("content") : root;

            // Step 2: If content is a string, parse it as JSON
            JsonNode data;
            if (contentNode.isTextual()) {
                data = objectMapper.readTree(contentNode.asText());
            } else {
                data = contentNode;
            }

            // Step 3: If data has a "message" field (GAMA nested wrapping), extract it
            if (data.has("message")) {
                JsonNode messageNode = data.get("message");
                if (messageNode.isTextual()) {
                    data = objectMapper.readTree(messageNode.asText());
                } else {
                    data = messageNode;
                }
            }

            logger.info("📋 Parsed GAMA data keys: {}", fieldNamesToString(data));

            GamaPayload payload = new GamaPayload();

            if (data.has("cycle")) {
                payload.setCycle(data.get("cycle").asInt());
            }
            if (data.has("nb_people")) {
                payload.setNbPeople(data.get("nb_people").asInt());
            }

            // Parse vehicles array
            if (data.has("vehicles") && data.get("vehicles").isArray()) {
                List<VehicleData> vehicles = new ArrayList<>();
                for (JsonNode v : data.get("vehicles")) {
                    VehicleData vehicle = new VehicleData();
                    vehicle.setId(v.has("id") ? v.get("id").asText() : "unknown");
                    vehicle.setX(v.has("x") ? v.get("x").asDouble() : 0);
                    vehicle.setY(v.has("y") ? v.get("y").asDouble() : 0);
                    vehicle.setSpeed(v.has("speed") ? v.get("speed").asDouble() : 0);
                    vehicle.setHeading(v.has("heading") ? v.get("heading").asDouble() : 0);
                    vehicle.setState(v.has("state") ? v.get("state").asText() : "unknown");
                    vehicles.add(vehicle);
                }
                payload.setVehicles(vehicles);
                logger.info("🚗 Parsed {} vehicles from cycle {}", vehicles.size(), payload.getCycle());
            } else {
                logger.warn("⚠️ No 'vehicles' array found in data. Available keys: {}", fieldNamesToString(data));
                // Log the first 500 chars of data for debugging
                String dataStr = data.toString();
                logger.warn("⚠️ Data content (first 500 chars): {}", 
                    dataStr.length() > 500 ? dataStr.substring(0, 500) : dataStr);
            }

            event.setPayload(payload);

        } catch (Exception e) {
            logger.error("❌ Could not parse GAMA message: {}", e.getMessage());
            logger.error("❌ Raw message (first 500 chars): {}", 
                rawMessage.length() > 500 ? rawMessage.substring(0, 500) : rawMessage);
            GamaPayload payload = new GamaPayload();
            payload.setCycle(-1);
            payload.setNbPeople(0);
            payload.setVehicles(new ArrayList<>());
            event.setPayload(payload);
            event.setEventType("RAW_OUTPUT");
        }

        return event;
    }

    public String toJson(GamaStateEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            logger.error("Failed to serialize GamaStateEvent to JSON", e);
            return "{}";
        }
    }

    private String fieldNamesToString(JsonNode node) {
        StringBuilder sb = new StringBuilder("[");
        node.fieldNames().forEachRemaining(f -> sb.append(f).append(", "));
        sb.append("]");
        return sb.toString();
    }
}
