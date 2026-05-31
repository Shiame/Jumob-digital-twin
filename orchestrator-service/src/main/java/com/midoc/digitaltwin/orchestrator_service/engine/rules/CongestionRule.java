package com.midoc.digitaltwin.orchestrator_service.engine.rules;

import com.midoc.digitaltwin.orchestrator_service.dto.carla.CarlaStateEvent;
import com.midoc.digitaltwin.orchestrator_service.dto.command.CarlaCommand;
import com.midoc.digitaltwin.orchestrator_service.dto.command.GamaCommand;
import com.midoc.digitaltwin.orchestrator_service.dto.gama.GamaRoad;
import com.midoc.digitaltwin.orchestrator_service.dto.gama.GamaStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scénario 3 — Congestion routière → Changement de feux dans CARLA
 *
 * SI GAMA détecte une congestion sur une route (congestionLevel > 0.8)
 * ALORS CARLA change les feux de signalisation pour faciliter la circulation.
 *
 * Flow:
 *   1. GAMA calcule le coefficient de congestion par route
 *   2. gama-adapter publie roads[].congestionLevel sur gama-state
 *   3. Orchestrateur vérifie : congestionLevel > seuil
 *   4. Orchestrateur envoie CHANGE_TRAFFIC_LIGHT sur carla-commands
 *   5. CARLA passe le feu au vert sur la route alternative
 */
//@Component
public class CongestionRule implements CoSimRule {

    private static final Logger logger = LoggerFactory.getLogger(CongestionRule.class);

    @Value("${cosim.rules.congestion.threshold:0.8}")
    private double congestionThreshold;

    /** Track which roads already have active green-light overrides to avoid spamming */
    private final Map<String, Boolean> activeOverrides = new HashMap<>();

    @Override
    public String getName() {
        return "S3_CONGESTION";
    }

    @Override
    public List<CarlaCommand> evaluateForCarla(GamaStateEvent gamaState, CarlaStateEvent carlaState) {
        if (gamaState == null || gamaState.getPayload() == null) {
            return Collections.emptyList();
        }

        List<GamaRoad> roads = gamaState.getPayload().getRoads();
        if (roads == null || roads.isEmpty()) {
            return Collections.emptyList();
        }

        List<CarlaCommand> commands = new ArrayList<>();

        for (GamaRoad road : roads) {
            boolean isCongested = road.getCongestionLevel() > congestionThreshold;
            Boolean wasOverridden = activeOverrides.getOrDefault(road.getRoadId(), false);

            if (isCongested && !wasOverridden) {
                // Congestion detected → change traffic light to green on this road
                activeOverrides.put(road.getRoadId(), true);

                String trafficLightId = mapRoadToTrafficLight(road.getRoadId());

                CarlaCommand cmd = CarlaCommand.builder()
                        .commandType("CHANGE_TRAFFIC_LIGHT")
                        .trafficLightId(trafficLightId)
                        .newState("GREEN")
                        .triggeredBy(getName())
                        .build();

                commands.add(cmd);

                logger.warn("🚦 [{}] Road '{}' congested (level: {}) → setting '{}' to GREEN",
                        getName(), road.getRoadId(), road.getCongestionLevel(), trafficLightId);

            } else if (!isCongested && wasOverridden) {
                // Congestion resolved → reset override
                activeOverrides.put(road.getRoadId(), false);

                logger.info("✅ [{}] Road '{}' congestion resolved (level: {}) — override cleared",
                        getName(), road.getRoadId(), road.getCongestionLevel());
            }
        }

        return commands;
    }

    @Override
    public List<GamaCommand> evaluateForGama(GamaStateEvent gamaState, CarlaStateEvent carlaState) {
        // This scenario only produces CARLA commands
        return Collections.emptyList();
    }

    /**
     * Maps a GAMA road ID to a CARLA traffic light ID.
     *
     * TODO: Replace with a proper mapping service using a shared infrastructure model.
     */
    private String mapRoadToTrafficLight(String roadId) {
        // Simple convention: traffic light ID derived from road ID
        return "tl_" + roadId.replace("road_", "");
    }
}
