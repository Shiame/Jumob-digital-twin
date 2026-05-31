package com.midoc.digitaltwin.orchestrator_service.dto.gama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single agent (pedestrian, bus, car, bike) from GAMA simulation.
 *
 * Field mapping:
 *   GAMA adapter sends: "id", "type", "x" (=longitude), "y" (=latitude),
 *   "speed", "heading", "acceleration", "roadId", "hostId", "hostType"
 *
 * IMPORTANT: agent["x"] = longitude, agent["y"] = latitude (EPSG:4326 / WGS84)
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GamaAgent {

    /** Agent unique identifier */
    @JsonProperty("id")
    private String id;

    /** Agent type: pedestrian, bike, car, bus */
    private String type;

    /** GPS longitude (EPSG:4326) — NOTE: GAMA sends this as "x" */
    private double x;

    /** GPS latitude (EPSG:4326) — NOTE: GAMA sends this as "y" */
    private double y;

    private double speed;
    private double heading;
    private double acceleration;

    /** Current GAMA road segment ID */
    private String roadId;

    /**
     * Host vehicle ID — non-null means this pedestrian is a passenger
     * inside a vehicle/bus/bike and should NOT be counted for density.
     */
    private String hostId;

    /** Type of host vehicle (bus, car, bike) */
    private String hostType;

    /** Agent state machine state: DRIVING, WAITING_TRANSPORT, ON_TRANSPORT, etc. */
    private String state;

    /** Simulation step when this agent was captured */
    private int simulationStep;

    // ── Convenience methods for geo-proximity rules ──

    /** Returns the longitude (geographic X coordinate) */
    public double getLongitude() {
        return x;
    }

    /** Returns the latitude (geographic Y coordinate) */
    public double getLatitude() {
        return y;
    }

    /** Returns true if this pedestrian is a passenger inside a vehicle */
    public boolean isPassenger() {
        return hostId != null && !hostId.isEmpty();
    }

    /** Returns true if this agent is a free (non-passenger) pedestrian */
    public boolean isFreePedestrian() {
        return "pedestrian".equalsIgnoreCase(type) && !isPassenger();
    }
}
