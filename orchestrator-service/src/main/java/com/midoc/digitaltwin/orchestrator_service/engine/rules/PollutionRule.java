package com.midoc.digitaltwin.orchestrator_service.engine.rules;

import com.midoc.digitaltwin.orchestrator_service.dto.carla.CarlaStateEvent;
import com.midoc.digitaltwin.orchestrator_service.dto.command.CarlaCommand;
import com.midoc.digitaltwin.orchestrator_service.dto.command.GamaCommand;
import com.midoc.digitaltwin.orchestrator_service.dto.gama.GamaStateEvent;
import com.midoc.digitaltwin.orchestrator_service.dto.gama.GamaZone;
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
 * Scénario 4 — Pollution → Restriction d'accès zone
 *
 * SI la pollution d'une zone dépasse un seuil critique (pollutionLevel > 0.9)
 * ALORS CARLA bloque l'entrée de la zone pour le véhicule autonome.
 *
 * Flow:
 *   1. GAMA calcule la pollution par zone (modèle de diffusion)
 *   2. gama-adapter publie zones[].pollutionLevel sur gama-state
 *   3. Orchestrateur vérifie : pollutionLevel > seuil
 *   4. Orchestrateur envoie BLOCK_ZONE_ENTRY sur carla-commands
 *   5. CARLA empêche le véhicule autonome d'entrer dans cette zone
 */
//@Component
public class PollutionRule implements CoSimRule {

    private static final Logger logger = LoggerFactory.getLogger(PollutionRule.class);

    @Value("${cosim.rules.pollution.threshold:0.9}")
    private double pollutionThreshold;

    /** Track which zones are currently blocked to avoid duplicate commands */
    private final Map<String, Boolean> blockedZones = new HashMap<>();

    @Override
    public String getName() {
        return "S4_POLLUTION";
    }

    @Override
    public List<CarlaCommand> evaluateForCarla(GamaStateEvent gamaState, CarlaStateEvent carlaState) {
        if (gamaState == null || gamaState.getPayload() == null) {
            return Collections.emptyList();
        }

        List<GamaZone> zones = gamaState.getPayload().getZones();
        if (zones == null || zones.isEmpty()) {
            return Collections.emptyList();
        }

        List<CarlaCommand> commands = new ArrayList<>();

        for (GamaZone zone : zones) {
            boolean isPolluted = zone.getPollutionLevel() > pollutionThreshold;
            Boolean wasBlocked = blockedZones.getOrDefault(zone.getZoneId(), false);

            if (isPolluted && !wasBlocked) {
                // Pollution critical → block zone entry
                blockedZones.put(zone.getZoneId(), true);

                CarlaCommand cmd = CarlaCommand.builder()
                        .commandType("BLOCK_ZONE_ENTRY")
                        .zoneId(zone.getZoneId())
                        .triggeredBy(getName())
                        .build();

                commands.add(cmd);

                logger.error("🏭 [{}] Zone '{}' pollution CRITICAL ({}) → blocking vehicle entry",
                        getName(), zone.getZoneId(), zone.getPollutionLevel());

            } else if (!isPolluted && wasBlocked) {
                // Pollution resolved → unblock
                blockedZones.put(zone.getZoneId(), false);

                logger.info("✅ [{}] Zone '{}' pollution resolved ({}) — zone re-opened",
                        getName(), zone.getZoneId(), zone.getPollutionLevel());
            }
        }

        return commands;
    }

    @Override
    public List<GamaCommand> evaluateForGama(GamaStateEvent gamaState, CarlaStateEvent carlaState) {
        // This scenario only produces CARLA commands
        return Collections.emptyList();
    }
}
