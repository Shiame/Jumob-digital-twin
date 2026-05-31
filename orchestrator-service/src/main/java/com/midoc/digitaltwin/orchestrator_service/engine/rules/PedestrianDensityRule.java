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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scénario 1 — Densité piétonne → Réduction de vitesse du véhicule autonome
 *
 * SI GAMA détecte beaucoup de piétons sur une route ciblée (roadId 2641 ou 2640)
 * ALORS l'orchestrateur envoie SET_SPEED_LIMIT vers CARLA avec les road_ids correspondants.
 *
 * Mapping temporaire (prototype) :
 *   GAMA roadId "2641" / "2640" → CARLA road_ids [1476, 1205, 1475]
 *
 * Ce mapping est manuel car le .xodr CARLA ne conserve pas les osmId.
 * Plus tard, il pourra être automatisé par comparaison géométrique.
 *
 * Seuils (configurables, bas pour le prototype) :
 *   - >= highThreshold piétons  → max 20 km/h  (HIGH_PEDESTRIAN_DENSITY)
 *   - >= mediumThreshold piétons → max 30 km/h (MEDIUM_PEDESTRIAN_DENSITY)
 *   - sinon                     → pas de commande
 *
 * DEPRECATED: Replaced by PedestrianDensityGeoRule which uses geographic
 * proximity instead of manual road-ID mapping. Remove @Component to disable.
 * Keep this class as reference for the road-based approach.
 */
// @Component  — DISABLED: replaced by PedestrianDensityGeoRule (geo-proximity)
public class PedestrianDensityRule implements CoSimRule {

    private static final Logger logger = LoggerFactory.getLogger(PedestrianDensityRule.class);

    // ═══════════════════════════════════════════════════════════════
    //  GAMA → CARLA Road Mapping (temporary / manual for prototype)
    //  One GAMA road can correspond to multiple CARLA road segments.
    // ═══════════════════════════════════════════════════════════════
    private static final Map<String, List<Integer>> GAMA_TO_CARLA_ROAD_MAPPING = Map.of(
            "2641", List.of(1476, 1205, 1475),
            "2642", List.of(1476, 1205, 1475),
            "2643", List.of(1476, 1205, 1475),
            "2644", List.of(1476, 1205, 1475),
            "2645", List.of(1476, 1205, 1475)
    );

    /** The set of GAMA roadIds we're monitoring for this prototype */
    private static final Set<String> TARGET_GAMA_ROAD_IDS = GAMA_TO_CARLA_ROAD_MAPPING.keySet();

    @Value("${cosim.rules.pedestrian-density.high-threshold:2}")
    private int highThreshold;

    @Value("${cosim.rules.pedestrian-density.high-speed-limit:20}")
    private int highSpeedLimit;

    @Value("${cosim.rules.pedestrian-density.medium-threshold:1}")
    private int mediumThreshold;

    @Value("${cosim.rules.pedestrian-density.medium-speed-limit:30}")
    private int mediumSpeedLimit;

    /** Track last sent speed limit per GAMA road to avoid spamming duplicate commands */
    private final Map<String, Integer> lastSentSpeedLimitPerRoad = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "S1_PEDESTRIAN_DENSITY";
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
            String roadId = road.getRoadId();

            // ── Only process our target GAMA roads ──
            if (roadId == null || !TARGET_GAMA_ROAD_IDS.contains(roadId)) {
                continue;
            }

            // ── Count pedestrians in movingAgents ──
            int pedestrians = 0;
            if (road.getMovingAgents() != null) {
                for (String agentType : road.getMovingAgents().values()) {
                    if ("pedestrian".equalsIgnoreCase(agentType)) {
                        pedestrians++;
                    }
                }
            }

            // ── Determine speed limit based on thresholds ──
            int speedLimit;
            String reason;

            if (pedestrians >= highThreshold) {
                speedLimit = highSpeedLimit;
                reason = "HIGH_PEDESTRIAN_DENSITY";
            } else if (pedestrians >= mediumThreshold) {
                speedLimit = mediumSpeedLimit;
                reason = "MEDIUM_PEDESTRIAN_DENSITY";
            } else {
                // Below threshold — check if we need to send a "clear" (reset)
                // For now, no command when below threshold
                logger.debug("[{}] GAMA road {} : {} pedestrians — below threshold, no action",
                        getName(), roadId, pedestrians);
                continue;
            }

            // ── Avoid duplicate commands ──
            Integer lastSent = lastSentSpeedLimitPerRoad.getOrDefault(roadId, -1);
            if (speedLimit == lastSent) {
                logger.debug("[{}] GAMA road {} : speed limit {} km/h already sent — skipping",
                        getName(), roadId, speedLimit);
                continue;
            }

            lastSentSpeedLimitPerRoad.put(roadId, speedLimit);

            // ── Resolve CARLA target roads ──
            List<Integer> targetCarlaRoadIds = GAMA_TO_CARLA_ROAD_MAPPING.get(roadId);

            // ── Build and emit the command ──
            CarlaCommand cmd = CarlaCommand.builder()
                    .commandType("SET_SPEED_LIMIT")
                    .maxSpeedKmh(speedLimit)
                    .sourceGamaRoadId(roadId)
                    .targetCarlaRoadIds(targetCarlaRoadIds)
                    .triggeredBy(getName())
                    .reason(reason)
                    .build();

            commands.add(cmd);

            logger.warn("⚠️ [{}] GAMA road {} mapped to CARLA roads {} : {} pedestrians → SET_SPEED_LIMIT {} km/h ({})",
                    getName(), roadId, targetCarlaRoadIds, pedestrians, speedLimit, reason);
        }

        return commands;
    }

    @Override
    public List<GamaCommand> evaluateForGama(GamaStateEvent gamaState, CarlaStateEvent carlaState) {
        // This scenario only produces CARLA commands
        return Collections.emptyList();
    }
}
