package com.midoc.digitaltwin.orchestrator_service.engine.rules;

import com.midoc.digitaltwin.orchestrator_service.dto.carla.CarlaStateEvent;
import com.midoc.digitaltwin.orchestrator_service.dto.carla.VehicleState;
import com.midoc.digitaltwin.orchestrator_service.dto.command.CarlaCommand;
import com.midoc.digitaltwin.orchestrator_service.dto.command.GamaCommand;
import com.midoc.digitaltwin.orchestrator_service.dto.gama.GamaAgent;
import com.midoc.digitaltwin.orchestrator_service.dto.gama.GamaStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * Scénario 1 (v2) — Pedestrian Density via Geographic Proximity
 *
 * Uses OpenStreetMap as the common geographic reference between GAMA and CARLA.
 * Instead of mapping GAMA road IDs to CARLA road IDs (which is fragile and
 * non-scalable), this rule computes geographic distance between the CARLA
 * ego vehicle and each GAMA pedestrian.
 *
 * Algorithm:
 *   1. Find the ego vehicle (role_name = "hero") in the latest CARLA state
 *   2. Extract its geographic coordinates (latitude, longitude)
 *   3. Extract free pedestrians from the latest GAMA state (exclude passengers)
 *   4. For each pedestrian, compute distance in meters using flat-Earth approximation
 *   5. Count pedestrians within a configurable radius
 *   6. If count >= threshold → publish SET_SPEED_LIMIT to carla-commands
 *   7. If count drops below threshold → publish RESET (restore normal speed)
 *
 * Why this is better than road-ID mapping:
 *   - Works even when GAMA/CARLA split the same OSM road differently
 *   - No manual mapping table needed
 *   - Scales to the entire map automatically
 *   - Uses OSM as the common spatial reference
 */
@Component
public class PedestrianDensityGeoRule implements CoSimRule {

    private static final Logger logger = LoggerFactory.getLogger(PedestrianDensityGeoRule.class);

    // ═══════════════════════════════════════════════════════════════
    //  Configuration (from application.properties / environment)
    // ═══════════════════════════════════════════════════════════════

    /** Radius in meters around the ego vehicle to count pedestrians */
    @Value("${cosim.rules.geo-proximity.radius-meters:30}")
    private int radiusMeters;

    /** High density threshold — triggers strong speed reduction */
    @Value("${cosim.rules.geo-proximity.high-threshold:10}")
    private int highThreshold;

    /** Speed limit when high density is detected (km/h) */
    @Value("${cosim.rules.geo-proximity.high-speed-limit:20}")
    private int highSpeedLimit;

    /** Medium density threshold — triggers moderate speed reduction */
    @Value("${cosim.rules.geo-proximity.medium-threshold:5}")
    private int mediumThreshold;

    /** Speed limit when medium density is detected (km/h) */
    @Value("${cosim.rules.geo-proximity.medium-speed-limit:30}")
    private int mediumSpeedLimit;

    /** Normal speed limit to restore when density clears */
    @Value("${cosim.rules.geo-proximity.normal-speed-limit:50}")
    private int normalSpeedLimit;

    // ═══════════════════════════════════════════════════════════════
    //  State tracking (avoid duplicate commands)
    // ═══════════════════════════════════════════════════════════════

    /** Last speed limit command sent — avoids spamming identical commands */
    private volatile int lastSentSpeedLimit = -1;

    /** Counter for logging throttle */
    private int evaluationCount = 0;

    @Override
    public String getName() {
        return "S1_PEDESTRIAN_DENSITY_GEO";
    }

    @Override
    public List<CarlaCommand> evaluateForCarla(GamaStateEvent gamaState, CarlaStateEvent carlaState) {
        evaluationCount++;

        // ── Need both states ──
        if (gamaState == null || gamaState.getPayload() == null) {
            return Collections.emptyList();
        }
        if (carlaState == null || carlaState.getPayload() == null) {
            return Collections.emptyList();
        }

        // ── Find the ego vehicle ──
        VehicleState ego = findEgoVehicle(carlaState);
        if (ego == null) {
            if (evaluationCount % 50 == 0) {
                logger.debug("[{}] No ego vehicle found in CARLA state", getName());
            }
            return Collections.emptyList();
        }

        double egoLat = ego.getLatitude();
        double egoLon = ego.getLongitude();

        // Validate ego coordinates
        if (egoLat == 0.0 || egoLon == 0.0) {
            if (evaluationCount % 50 == 0) {
                logger.warn("[{}] Ego vehicle has zero lat/lon — geo-proximity disabled", getName());
            }
            return Collections.emptyList();
        }

        // ── Extract and filter GAMA pedestrians ──
        List<GamaAgent> agents = gamaState.getPayload().getAgents();
        if (agents == null || agents.isEmpty()) {
            return Collections.emptyList();
        }

        int totalPedestrians = 0;
        int passengers = 0;
        int validPedestrians = 0;
        int nearbyPedestrians = 0;

        for (GamaAgent agent : agents) {
            if (!"pedestrian".equalsIgnoreCase(agent.getType())) {
                continue;
            }
            totalPedestrians++;

            // Skip passengers (inside vehicles/buses)
            if (agent.isPassenger()) {
                passengers++;
                continue;
            }
            validPedestrians++;

            double pedLat = agent.getLatitude();
            double pedLon = agent.getLongitude();

            // Skip invalid coordinates
            if (pedLat == 0.0 || pedLon == 0.0) {
                continue;
            }

            // Compute distance
            double distance = distanceMeters(egoLat, egoLon, pedLat, pedLon);
            if (distance <= radiusMeters) {
                nearbyPedestrians++;
            }
        }

        // ── Periodic logging ──
        if (evaluationCount % 20 == 0) {
            logger.info("[{}] Ego at (lat={}, lon={}) | total_peds={} | passengers={} | valid={} | within_{}m={}",
                    getName(),
                    String.format("%.6f", egoLat),
                    String.format("%.6f", egoLon),
                    totalPedestrians, passengers, validPedestrians,
                    radiusMeters, nearbyPedestrians);
        }

        // ── Evaluate thresholds ──
        int speedLimit;
        String reason;

        if (nearbyPedestrians >= highThreshold) {
            speedLimit = highSpeedLimit;
            reason = "HIGH_PEDESTRIAN_DENSITY";
        } else if (nearbyPedestrians >= mediumThreshold) {
            speedLimit = mediumSpeedLimit;
            reason = "MEDIUM_PEDESTRIAN_DENSITY";
        } else {
            // Below threshold — Normal operation, no command needed
            // LIGNE COMMENTEE POUR LE TEST : On ne reset plus la vitesse, la voiture reste lente
            /*
            if (lastSentSpeedLimit > 0 && lastSentSpeedLimit < normalSpeedLimit) {
                // Was slowed down, now density cleared → reset speed
                speedLimit = normalSpeedLimit;
                reason = "PEDESTRIAN_DENSITY_CLEARED";
            } else {
            */
                return Collections.emptyList();
            // }
        }

        // ── POUR LE TEST: on envoie à chaque évaluation (pas de déduplication) ──
        // if (speedLimit == lastSentSpeedLimit) {
        //     return Collections.emptyList();
        // }

        lastSentSpeedLimit = speedLimit;

        // ── Build and return the command ──
        CarlaCommand cmd = CarlaCommand.builder()
                .commandType("SET_SPEED_LIMIT")
                .maxSpeedKmh(speedLimit)
                .reason(reason)
                .alignmentMethod("GEO_PROXIMITY")
                .targetVehicleId("ego")
                .pedestrianCount(nearbyPedestrians)
                .radiusMeters(radiusMeters)
                .egoLatitude(egoLat)
                .egoLongitude(egoLon)
                .triggeredBy(getName())
                .build();

        logger.warn("⚠️ [{}] {} pedestrians within {}m of ego → SET_SPEED_LIMIT {} km/h ({})",
                getName(), nearbyPedestrians, radiusMeters, speedLimit, reason);

        return List.of(cmd);
    }

    @Override
    public List<GamaCommand> evaluateForGama(GamaStateEvent gamaState, CarlaStateEvent carlaState) {
        // This scenario only produces CARLA commands
        return Collections.emptyList();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Find the ego vehicle (role_name = "hero") in the CARLA state.
     * Falls back to the first vehicle if no hero is found.
     */
    private VehicleState findEgoVehicle(CarlaStateEvent carlaState) {
        List<VehicleState> vehicles = carlaState.getPayload().getVehicles();
        if (vehicles == null || vehicles.isEmpty()) {
            return null;
        }

        // Priority: vehicle with role_name = "hero"
        for (VehicleState v : vehicles) {
            if (v.isEgo()) {
                return v;
            }
        }

        // Fallback: first vehicle
        return vehicles.get(0);
    }

    /**
     * Compute approximate distance in meters between two geographic points.
     *
     * Uses flat-Earth approximation which is accurate to < 0.01% for distances
     * under 1km at Toulouse latitude (43.5°N). This is essentially exact for
     * our 30m radius use case.
     *
     * @param lat1 Latitude of point 1 (degrees)
     * @param lon1 Longitude of point 1 (degrees)
     * @param lat2 Latitude of point 2 (degrees)
     * @param lon2 Longitude of point 2 (degrees)
     * @return Distance in meters
     */
    static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final double METERS_PER_DEG_LAT = 111_320.0;
        double metersPerDegLon = 111_320.0 * Math.cos(Math.toRadians(lat1));

        double dx = (lon2 - lon1) * metersPerDegLon;
        double dy = (lat2 - lat1) * METERS_PER_DEG_LAT;

        return Math.sqrt(dx * dx + dy * dy);
    }
}
