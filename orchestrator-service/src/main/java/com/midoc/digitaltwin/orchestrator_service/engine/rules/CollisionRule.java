package com.midoc.digitaltwin.orchestrator_service.engine.rules;

import com.midoc.digitaltwin.orchestrator_service.dto.carla.CarlaEvent;
import com.midoc.digitaltwin.orchestrator_service.dto.carla.CarlaStateEvent;
import com.midoc.digitaltwin.orchestrator_service.dto.command.CarlaCommand;
import com.midoc.digitaltwin.orchestrator_service.dto.command.GamaCommand;
import com.midoc.digitaltwin.orchestrator_service.dto.gama.GamaStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scénario 2 — Collision du véhicule autonome → Fermeture de route dans GAMA
 *
 * SI CARLA détecte une collision (capteurs physiques)
 * ALORS GAMA ferme le segment de route et reroute ses agents.
 *
 * Flow:
 *   1. CARLA détecte la collision via capteur
 *   2. carla-adapter publie COLLISION_EVENT sur carla-state
 *   3. Orchestrateur mappe la position vers un roadId GAMA
 *   4. Orchestrateur envoie BLOCK_ROAD sur gama-commands
 *   5. GAMA reroute piétons, bus, vélos autour de la route bloquée
 */
//@Component
public class CollisionRule implements CoSimRule {

    private static final Logger logger = LoggerFactory.getLogger(CollisionRule.class);

    /** Track collision event IDs we've already processed to avoid duplicate commands */
    private final Set<String> processedCollisions = new HashSet<>();

    @Override
    public String getName() {
        return "S2_COLLISION";
    }

    @Override
    public List<CarlaCommand> evaluateForCarla(GamaStateEvent gamaState, CarlaStateEvent carlaState) {
        // This scenario only produces GAMA commands
        return Collections.emptyList();
    }

    @Override
    public List<GamaCommand> evaluateForGama(GamaStateEvent gamaState, CarlaStateEvent carlaState) {
        if (carlaState == null || carlaState.getPayload() == null) {
            return Collections.emptyList();
        }

        List<CarlaEvent> events = carlaState.getPayload().getEvents();
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        List<GamaCommand> commands = new ArrayList<>();

        for (CarlaEvent event : events) {
            if (!"COLLISION".equalsIgnoreCase(event.getType())) {
                continue;
            }

            // Create a unique key for this collision to avoid duplicates
            String collisionKey = String.format("%.0f_%.0f_%s",
                    event.getPosition() != null ? event.getPosition().getX() : 0,
                    event.getPosition() != null ? event.getPosition().getY() : 0,
                    carlaState.getEventId());

            if (processedCollisions.contains(collisionKey)) {
                continue;
            }
            processedCollisions.add(collisionKey);

            // Map CARLA position to a GAMA roadId
            // For now, we use a simple mapping based on position quadrants
            // In production, this would use a proper coordinate mapping service
            String roadId = mapPositionToRoadId(event.getPosition());

            GamaCommand cmd = GamaCommand.builder()
                    .commandType("BLOCK_ROAD")
                    .roadId(roadId)
                    .blocked(true)
                    .triggeredBy(getName())
                    .build();

            commands.add(cmd);

            logger.error("🚨 [{}] COLLISION detected at ({}, {}) — severity: {} — blocking road '{}'",
                    getName(),
                    event.getPosition() != null ? event.getPosition().getX() : "?",
                    event.getPosition() != null ? event.getPosition().getY() : "?",
                    event.getSeverity(),
                    roadId);
        }

        // Limit memory: keep only the last 1000 processed collisions
        if (processedCollisions.size() > 1000) {
            processedCollisions.clear();
        }

        return commands;
    }

    /**
     * Maps a CARLA world position to a GAMA road ID.
     *
     * TODO: Replace with a proper coordinate mapping service that uses
     * a shared road network graph between GAMA and CARLA.
     * For now, uses a simple grid-based mapping.
     */
    private String mapPositionToRoadId(CarlaEvent.Position position) {
        if (position == null) {
            return "road_unknown";
        }

        // Simple grid mapping (placeholder)
        int gridX = (int) (position.getX() / 50.0);
        int gridY = (int) (position.getY() / 50.0);
        return String.format("road_%d_%d", Math.abs(gridX), Math.abs(gridY));
    }
}
