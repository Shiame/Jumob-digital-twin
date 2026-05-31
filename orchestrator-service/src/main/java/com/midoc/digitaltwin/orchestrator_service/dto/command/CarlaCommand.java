package com.midoc.digitaltwin.orchestrator_service.dto.command;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Command sent to the CARLA Adapter via Kafka topic 'carla-commands'.
 * The adapter will interpret commandType and apply the corresponding action
 * on the CARLA simulator.
 *
 * Supported commandTypes:
 *   - SET_SPEED_LIMIT    → Limit ego vehicle speed (payload: maxSpeedKmh)
 *   - BLOCK_ZONE_ENTRY   → Block vehicle from entering a zone (payload: zoneId)
 *   - CHANGE_TRAFFIC_LIGHT → Change a traffic light state (payload: trafficLightId, newState)
 *   - SPAWN_NPC           → Spawn a GAMA agent as NPC in CARLA (payload: agentId, x, y, type)
 *
 * Alignment modes:
 *   - ROAD_MAPPING: legacy road-ID based mapping (sourceGamaRoadId → targetCarlaRoadIds)
 *   - GEO_PROXIMITY: geographic proximity based (no road IDs needed)
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CarlaCommand {

    @Builder.Default
    private String commandId = "cmd-" + UUID.randomUUID().toString().substring(0, 8);

    private String commandType;

    // --- SET_SPEED_LIMIT ---
    private Integer maxSpeedKmh;

    /** Reason for the command (e.g. HIGH_PEDESTRIAN_DENSITY, MEDIUM_PEDESTRIAN_DENSITY) */
    private String reason;

    // --- Alignment method ---
    /** "GEO_PROXIMITY" or "ROAD_MAPPING" — tells the CARLA adapter how to apply the command */
    private String alignmentMethod;

    // --- Geo-proximity details (used when alignmentMethod = GEO_PROXIMITY) ---
    /** Target vehicle identifier ("ego") */
    private String targetVehicleId;

    /** Number of pedestrians detected within the radius */
    private Integer pedestrianCount;

    /** Radius in meters used for the proximity check */
    private Integer radiusMeters;

    /** Ego vehicle latitude at the time of the command */
    private Double egoLatitude;

    /** Ego vehicle longitude at the time of the command */
    private Double egoLongitude;

    // --- Road-based mapping (legacy, used when alignmentMethod = ROAD_MAPPING) ---
    /** The GAMA roadId that triggered this command */
    private String sourceGamaRoadId;

    /** The corresponding CARLA road_ids (one GAMA road → multiple CARLA segments) */
    private List<Integer> targetCarlaRoadIds;

    // --- BLOCK_ZONE_ENTRY ---
    private String zoneId;

    // --- CHANGE_TRAFFIC_LIGHT ---
    private String trafficLightId;
    private String newState;  // GREEN, RED, YELLOW

    // --- SPAWN_NPC ---
    private String agentId;
    private Double x;
    private Double y;
    private String agentType; // pieton, bus, velo, etc.

    // --- Context (which scenario triggered this) ---
    private String triggeredBy;
}
