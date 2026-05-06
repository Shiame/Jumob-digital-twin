package com.gama_adapter.gama_adapter.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gama_adapter.gama_adapter.dto.GamaStateEvent;
import com.gama_adapter.gama_adapter.dto.GamaStateEvent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Transforms raw GAMA WebSocket output into structured GamaStateEvent DTOs.
 *
 * Supports two formats:
 *
 *  1) Legacy flat format (simple Traffic and Pollution model):
 *     { "type":"traffic_data", "cycle":N, "vehicles":[...] }
 *
 *  2) Bruno's MobilitySimulator format (statusesByStep):
 *     { "experimentID":"...", "objectDescription":"movingAgentStatusesByStep",
 *       "fragmentID":N, "statusesByStep":{ "42":[...agents...] } }
 *
 * Bruno sends 5 separate message types that we parse and emit independently:
 *   - movingAgentStatusesByStep   → vehicles + pedestrians
 *   - trafficSignalsStatusesByStep → traffic light states
 *   - roadsStatusesByStep          → road occupation score + congestion
 *   - busStopsStatusesByStep       → waiting pedestrians per stop
 *   - parkingsStatusesByStep       → parked vehicle counts
 */
@Service
public class GamaDataTransformer {

    private static final Logger logger = LoggerFactory.getLogger(GamaDataTransformer.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);

    // Parking capacity threshold: if parkedVehicles > this percentage of max observed, mark as full
    private static final int PARKING_FULL_THRESHOLD = 50;
    // Pollution score: occupation above this triggers high-pollution flag
    private static final double POLLUTION_OCCUPATION_THRESHOLD = 0.7;

    /**
     * Main entry point. Detects the message format and dispatches to the correct parser.
     */
    public GamaStateEvent transform(String rawMessage) {
        try {
            JsonNode root = objectMapper.readTree(rawMessage);

            // --- Unwrap GAMA Server multi-level wrapping ---
            JsonNode data = unwrap(root);

            // --- Detect format ---
            if (data.has("objectDescription")) {
                return parseBrunoFormat(data);
            } else {
                return parseLegacyFormat(data);
            }

        } catch (Exception e) {
            logger.error("Could not parse GAMA message: {}", e.getMessage());
            logger.error("Raw (first 300): {}", rawMessage.length() > 300 ? rawMessage.substring(0, 300) : rawMessage);
            return buildErrorEvent();
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  GAMA Server message unwrapping
    // ───────────────────────────────────────────────────────────────────────────

    private JsonNode unwrap(JsonNode root) throws Exception {
        // Level 1: { "content": "..." } or { "content": { "message": "..." } }
        JsonNode data = root.has("content") ? root.get("content") : root;

        if (data.isTextual()) {
            data = objectMapper.readTree(data.asText());
        }
        if (data.has("message")) {
            JsonNode msg = data.get("message");
            data = msg.isTextual() ? objectMapper.readTree(msg.asText()) : msg;
        }
        return data;
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Bruno format parser — dispatches by objectDescription
    // ───────────────────────────────────────────────────────────────────────────

    private GamaStateEvent parseBrunoFormat(JsonNode data) {
        String objDesc = data.get("objectDescription").asText();
        String experimentId = data.has("experimentID") ? data.get("experimentID").asText() : "";
        int fragmentId = data.has("fragmentID") ? data.get("fragmentID").asInt() : 0;

        GamaStateEvent event = new GamaStateEvent();
        event.setEventType("BRUNO_" + objDesc.toUpperCase());

        GamaPayload payload = new GamaPayload();
        payload.setExperimentId(experimentId);
        payload.setFragmentId(fragmentId);

        JsonNode statusesByStep = data.has("statusesByStep") ? data.get("statusesByStep") : null;

        if (statusesByStep == null || !statusesByStep.isObject()) {
            logger.warn("No 'statusesByStep' in message type: {}", objDesc);
            event.setPayload(payload);
            return event;
        }

        // Extract the first (and usually only) step key for cycle number
        int cycle = extractFirstStepKey(statusesByStep);
        payload.setCycle(cycle);

        switch (objDesc) {
            case "movingAgentStatusesByStep" -> parseMovingAgents(statusesByStep, payload);
            case "trafficSignalsStatusesByStep" -> parseTrafficSignals(statusesByStep, payload);
            case "roadsStatusesByStep" -> parseRoads(statusesByStep, payload);
            case "busStopsStatusesByStep" -> parseBusStops(statusesByStep, payload);
            case "parkingsStatusesByStep" -> parseParkings(statusesByStep, payload);
            default -> logger.warn("Unknown Bruno objectDescription: {}", objDesc);
        }

        event.setPayload(payload);
        logger.info("📦 Bruno [{}] → cycle={} | vehicles={} | pedestrians={} | signals={} | roads={} | busStops={} | parkings={}",
            objDesc, cycle,
            payload.getVehicles().size(),
            payload.getPedestrians().size(),
            payload.getTrafficSignals().size(),
            payload.getRoads().size(),
            payload.getBusStops().size(),
            payload.getParkings().size());

        return event;
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Moving agents (vehicles + pedestrians)
    // ───────────────────────────────────────────────────────────────────────────

    private void parseMovingAgents(JsonNode statusesByStep, GamaPayload payload) {
        List<VehicleData> vehicles = new ArrayList<>();
        List<VehicleData> pedestrians = new ArrayList<>();

        for (JsonNode stepAgents : statusesByStep) {
            for (JsonNode a : stepAgents) {
                VehicleData agent = new VehicleData();
                agent.setId(getStr(a, "id", "unknown"));
                agent.setType(getStr(a, "type", "car"));
                agent.setSimulationStep(getInt(a, "simulationStep", 0));

                // GPS coordinates from Bruno's EPSG:4326 conversion
                JsonNode gps = a.has("GPSCoordinates") ? a.get("GPSCoordinates") : null;
                if (gps != null) {
                    agent.setX(getDbl(gps, "lon", 0.0));
                    agent.setY(getDbl(gps, "lat", 0.0));
                }

                agent.setSpeed(getDbl(a, "speed", 0.0));
                agent.setHeading(getDbl(a, "heading", 0.0));
                agent.setAcceleration(getDbl(a, "acceleration", 0.0));
                agent.setRoadId(getStr(a, "currentRoadId", null));
                agent.setSourceIntersectionId(getStr(a, "sourceIntersectionId", null));
                agent.setTargetIntersectionId(getStr(a, "targetIntersectionId", null));
                agent.setCurrentLane(getInt(a, "currentLane", 0));
                agent.setLength(getDbl(a, "length", 0.0));
                agent.setWidth(getDbl(a, "width", 0.0));
                agent.setNumberOfPassengers(getInt(a, "numberOfPassengers", 0));

                // Passengers list (for vehicles carrying pedestrians)
                if (a.has("passengers") && a.get("passengers").isArray()) {
                    List<String> passList = new ArrayList<>();
                    a.get("passengers").forEach(p -> passList.add(p.asText()));
                    agent.setPassengers(passList);
                }

                // Host (for pedestrians on a bus)
                agent.setHostId(getStr(a, "host", null));
                agent.setHostType(getStr(a, "hostType", null));

                String type = agent.getType();
                if ("pedestrian".equals(type)) {
                    pedestrians.add(agent);
                } else {
                    vehicles.add(agent);
                }
            }
        }

        payload.setVehicles(vehicles);
        payload.setPedestrians(pedestrians);

        // agents = vehicles + pedestrians (backward compat)
        List<VehicleData> all = new ArrayList<>(vehicles);
        all.addAll(pedestrians);
        payload.setAgents(all);
        payload.setNbPeople(pedestrians.size());

        logger.info("  → {} vehicles, {} pedestrians", vehicles.size(), pedestrians.size());
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Traffic signals — drives CARLA traffic light synchronization
    // ───────────────────────────────────────────────────────────────────────────

    private void parseTrafficSignals(JsonNode statusesByStep, GamaPayload payload) {
        List<TrafficSignalData> signals = new ArrayList<>();

        for (JsonNode stepSignals : statusesByStep) {
            for (JsonNode s : stepSignals) {
                TrafficSignalData signal = new TrafficSignalData();
                signal.setId(getStr(s, "id", "unknown"));
                signal.setName(getStr(s, "name", ""));
                signal.setState(getStr(s, "state", "red")); // "green" or "red"
                signal.setSimulationStep(getInt(s, "simulationStep", 0));

                JsonNode gps = s.has("GPSCoordinates") ? s.get("GPSCoordinates") : null;
                if (gps != null) {
                    signal.setLon(getDbl(gps, "lon", 0.0));
                    signal.setLat(getDbl(gps, "lat", 0.0));
                }
                signals.add(signal);
            }
        }

        payload.setTrafficSignals(signals);
        logger.info("  → {} traffic signals", signals.size());
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Roads — congestion + occupation + pollution score calculation
    // ───────────────────────────────────────────────────────────────────────────

    private void parseRoads(JsonNode statusesByStep, GamaPayload payload) {
        List<RoadData> roads = new ArrayList<>();

        for (JsonNode stepRoads : statusesByStep) {
            for (JsonNode r : stepRoads) {
                RoadData road = new RoadData();
                road.setRoadId(getStr(r, "id", "unknown"));
                road.setOsmId(getStr(r, "osmId", ""));
                road.setSimulationStep(getInt(r, "simulationStep", 0));

                double occScore = getDbl(r, "roadOccupationScore", 0.0);
                road.setOccupationScore(occScore);

                // Congestion = 1 - occupationScore (more occupied = more congested)
                road.setCongestionLevel(occScore);

                // Pollution score: derived from occupation (proxy for emissions)
                // High occupation + low speed → high pollution
                double pollution = occScore > POLLUTION_OCCUPATION_THRESHOLD ? occScore * 1.5 : occScore;
                road.setPollutionScore(Math.min(pollution, 1.0));

                // Moving agents map: agentId → agentType
                if (r.has("movingAgents") && r.get("movingAgents").isObject()) {
                    Map<String, String> agentMap = new HashMap<>();
                    r.get("movingAgents").fields().forEachRemaining(
                        e -> agentMap.put(e.getKey(), e.getValue().asText())
                    );
                    road.setMovingAgents(agentMap);
                }

                roads.add(road);
            }
        }

        payload.setRoads(roads);
        logger.info("  → {} roads (high pollution: {})",
            roads.size(),
            roads.stream().filter(r -> r.getPollutionScore() > POLLUTION_OCCUPATION_THRESHOLD).count());
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Bus stops — pedestrian density at stops
    // ───────────────────────────────────────────────────────────────────────────

    private void parseBusStops(JsonNode statusesByStep, GamaPayload payload) {
        List<BusStopData> busStops = new ArrayList<>();

        for (JsonNode stepStops : statusesByStep) {
            for (JsonNode s : stepStops) {
                BusStopData stop = new BusStopData();
                stop.setStopId(getStr(s, "id", "unknown"));
                stop.setSimulationStep(getInt(s, "simulationStep", 0));

                if (s.has("waitingPedestrians") && s.get("waitingPedestrians").isArray()) {
                    List<String> waiting = new ArrayList<>();
                    s.get("waitingPedestrians").forEach(p -> waiting.add(p.asText()));
                    stop.setWaitingPedestrians(waiting); // also sets waitingCount
                }
                busStops.add(stop);
            }
        }

        payload.setBusStops(busStops);
        logger.info("  → {} bus stops (total waiting: {})",
            busStops.size(),
            busStops.stream().mapToInt(BusStopData::getWaitingCount).sum());
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Parkings — redirect CARLA vehicles when parking is full
    // ───────────────────────────────────────────────────────────────────────────

    private void parseParkings(JsonNode statusesByStep, GamaPayload payload) {
        List<ParkingData> parkings = new ArrayList<>();

        for (JsonNode stepParkings : statusesByStep) {
            for (JsonNode p : stepParkings) {
                ParkingData parking = new ParkingData();
                parking.setParkingId(getStr(p, "id", "unknown"));
                parking.setSimulationStep(getInt(p, "simulationStep", 0));

                int parked = getInt(p, "parkedVehicules", 0); // Note: Bruno uses "parkedVehicules"
                parking.setParkedVehicles(parked);
                parking.setFull(parked >= PARKING_FULL_THRESHOLD);

                parkings.add(parking);
            }
        }

        payload.setParkings(parkings);
        logger.info("  → {} parkings ({} full)",
            parkings.size(),
            parkings.stream().filter(ParkingData::isFull).count());
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Legacy flat format (old Traffic and Pollution model)
    // ───────────────────────────────────────────────────────────────────────────

    private GamaStateEvent parseLegacyFormat(JsonNode data) {
        GamaStateEvent event = new GamaStateEvent();
        GamaPayload payload = new GamaPayload();

        if (data.has("cycle")) payload.setCycle(data.get("cycle").asInt());
        if (data.has("nb_people")) payload.setNbPeople(data.get("nb_people").asInt());

        if (data.has("vehicles") && data.get("vehicles").isArray()) {
            List<VehicleData> vehicles = new ArrayList<>();
            for (JsonNode v : data.get("vehicles")) {
                VehicleData vehicle = new VehicleData();
                vehicle.setId(getStr(v, "id", "unknown"));
                vehicle.setType(getStr(v, "type", "car"));
                vehicle.setX(getDbl(v, "x", 0));
                vehicle.setY(getDbl(v, "y", 0));
                vehicle.setSpeed(getDbl(v, "speed", 0));
                vehicle.setHeading(getDbl(v, "heading", 0));
                vehicle.setRoadId(getStr(v, "road_id", null));
                vehicle.setState(getStr(v, "state", null));
                vehicles.add(vehicle);
            }
            payload.setVehicles(vehicles);
            payload.setAgents(vehicles);
            logger.info("📊 Legacy format: {} vehicles, cycle={}", vehicles.size(), payload.getCycle());
        }

        // Auto-generate zone from nb_people (backward compat)
        if (payload.getNbPeople() > 0) {
            ZoneData zone = new ZoneData();
            zone.setZoneId("zone_centre");
            zone.setPedestrianCount(payload.getNbPeople());
            zone.setPollutionLevel(0.0);
            payload.setZones(List.of(zone));
        }

        event.setPayload(payload);
        return event;
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Serialization
    // ───────────────────────────────────────────────────────────────────────────

    public String toJson(GamaStateEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            logger.error("Failed to serialize GamaStateEvent", e);
            return "{}";
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ───────────────────────────────────────────────────────────────────────────

    private int extractFirstStepKey(JsonNode statusesByStep) {
        Iterator<String> keys = statusesByStep.fieldNames();
        if (keys.hasNext()) {
            try { return Integer.parseInt(keys.next()); } catch (NumberFormatException e) { /* ignore */ }
        }
        return 0;
    }

    private String getStr(JsonNode node, String field, String defaultVal) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : defaultVal;
    }

    private double getDbl(JsonNode node, String field, double defaultVal) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asDouble() : defaultVal;
    }

    private int getInt(JsonNode node, String field, int defaultVal) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : defaultVal;
    }

    private GamaStateEvent buildErrorEvent() {
        GamaStateEvent event = new GamaStateEvent();
        event.setEventType("PARSE_ERROR");
        GamaPayload payload = new GamaPayload();
        payload.setCycle(-1);
        event.setPayload(payload);
        return event;
    }
}
