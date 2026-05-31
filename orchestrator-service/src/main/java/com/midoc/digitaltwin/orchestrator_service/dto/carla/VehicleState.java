package com.midoc.digitaltwin.orchestrator_service.dto.carla;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * State of a single vehicle actor in the CARLA simulation.
 * Maps to the VehicleState published by carla-adapter.
 *
 * Now includes geographic coordinates (latitude/longitude) for
 * geo-proximity co-simulation rules, and role_name to identify
 * the ego vehicle ("hero").
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleState {
    private int id;
    private String type;      // e.g. vehicle.tesla.model3

    // Position (world coordinates, meters)
    private double x;
    private double y;
    private double z;

    // Geographic coordinates (WGS84) — for co-simulation geo-proximity rules
    private double latitude;
    private double longitude;

    // Rotation (degrees)
    private double pitch;
    private double yaw;
    private double roll;

    // Velocity (m/s)
    private double vx;
    private double vy;
    private double vz;

    // Derived
    @JsonProperty("speed_kmh")
    private double speedKmh;

    // Role — identifies the ego vehicle ("hero") vs NPC traffic
    @JsonProperty("role_name")
    private String roleName;

    /** Returns true if this vehicle is the ego (hero) vehicle */
    public boolean isEgo() {
        return "hero".equalsIgnoreCase(roleName);
    }
}
