package com.midoc.digitaltwin.orchestrator_service.engine.rules;

import com.midoc.digitaltwin.orchestrator_service.dto.carla.CarlaEvent;
import com.midoc.digitaltwin.orchestrator_service.dto.carla.CarlaStateEvent;
import com.midoc.digitaltwin.orchestrator_service.dto.command.CarlaCommand;
import com.midoc.digitaltwin.orchestrator_service.dto.command.GamaCommand;
import com.midoc.digitaltwin.orchestrator_service.dto.gama.GamaStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Scénario 2 — Collision du véhicule autonome → Fermeture de route dans GAMA
 *
 * SI CARLA détecte une collision (capteurs physiques)
 * ALORS GAMA ferme le segment de route et reroute ses agents.
 *
 * IMPORTANT: Uses a 60-second cooldown to avoid flooding GAMA with
 * hundreds of duplicate BLOCK_ROAD commands (the collision sensor fires
 * continuously while the two vehicles are overlapping).
 */
@Component
public class CollisionRule implements CoSimRule {

    private static final Logger logger = LoggerFactory.getLogger(CollisionRule.class);

    /** Cooldown period in milliseconds — ignore collisions for 60s after the first one */
    private static final long COOLDOWN_MS = 60_000;

    /** Timestamp of the last collision we processed */
    private volatile long lastCollisionTimestamp = 0;

    /** Whether we already sent the STOP command to CARLA for this collision */
    private volatile boolean carlaStopSent = false;

    @Override
    public String getName() {
        return "S2_COLLISION";
    }

    @Override
    public List<CarlaCommand> evaluateForCarla(GamaStateEvent gamaState, CarlaStateEvent carlaState) {
        if (carlaState == null || carlaState.getPayload() == null) {
            return Collections.emptyList();
        }

        // If we already sent STOP for this collision, don't send again
        if (carlaStopSent && !isCooldownExpired()) {
            return Collections.emptyList();
        }

        List<CarlaEvent> events = carlaState.getPayload().getEvents();
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        for (CarlaEvent event : events) {
            if ("COLLISION".equalsIgnoreCase(event.getType())) {
                carlaStopSent = true;

                CarlaCommand cmd = CarlaCommand.builder()
                        .commandType("SET_SPEED_LIMIT")
                        .maxSpeedKmh(0)
                        .reason("ACCIDENT DETECTED! Stopping vehicle.")
                        .triggeredBy(getName())
                        .build();

                logger.warn("🚨 [{}] Sending STOP command to CARLA vehicle!", getName());
                return Collections.singletonList(cmd);
            }
        }

        return Collections.emptyList();
    }

    @Override
    public List<GamaCommand> evaluateForGama(GamaStateEvent gamaState, CarlaStateEvent carlaState) {
        if (carlaState == null || carlaState.getPayload() == null) {
            return Collections.emptyList();
        }

        // COOLDOWN: If we already processed a collision recently, skip entirely
        if (!isCooldownExpired() && lastCollisionTimestamp > 0) {
            return Collections.emptyList();
        }

        List<CarlaEvent> events = carlaState.getPayload().getEvents();
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        for (CarlaEvent event : events) {
            if ("COLLISION".equalsIgnoreCase(event.getType())) {
                // Mark the time — no more commands for 60 seconds
                lastCollisionTimestamp = System.currentTimeMillis();
                carlaStopSent = false; // reset for CARLA side too

                String roadId = "1.45950994,43.55287609";

                logger.error("🚨 [{}] COLLISION detected at ({}, {}) — severity: {} — blocking road '{}' — COOLDOWN 60s ACTIVATED",
                        getName(),
                        event.getPosition() != null ? event.getPosition().getX() : "?",
                        event.getPosition() != null ? event.getPosition().getY() : "?",
                        event.getSeverity(),
                        roadId);

                GamaCommand cmd = GamaCommand.builder()
                        .commandType("BLOCK_ROAD")
                        .roadId(roadId)
                        .blocked(true)
                        .triggeredBy(getName())
                        .build();

                // Return ONLY ONE command, then cooldown blocks everything else
                return Collections.singletonList(cmd);
            }
        }

        return Collections.emptyList();
    }

    /** Check if the cooldown period has expired */
    private boolean isCooldownExpired() {
        return System.currentTimeMillis() - lastCollisionTimestamp > COOLDOWN_MS;
    }
}
