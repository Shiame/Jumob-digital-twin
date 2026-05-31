package com.midoc.digitaltwin.orchestrator_service.controller;

import com.midoc.digitaltwin.orchestrator_service.engine.CoSimulationEngine;
import com.midoc.digitaltwin.orchestrator_service.engine.StateManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller providing health and detailed status endpoints
 * for monitoring the Co-Simulation Engine.
 *
 * Endpoints:
 *   GET /api/health  → Quick health check (UP/DOWN)
 *   GET /api/status  → Detailed engine status with metrics
 */
@RestController
@RequestMapping("/api")
public class OrchestratorController {

    private final CoSimulationEngine engine;
    private final StateManager stateManager;

    public OrchestratorController(CoSimulationEngine engine, StateManager stateManager) {
        this.engine = engine;
        this.stateManager = stateManager;
    }

    /**
     * Quick health check.
     * Returns UP if the engine has received at least one message from either simulator.
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        boolean gamaConnected = stateManager.getGamaState() != null;
        boolean carlaConnected = stateManager.getCarlaState() != null;
        boolean isHealthy = gamaConnected || carlaConnected;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", isHealthy ? "UP" : "WAITING");
        response.put("gamaState", gamaConnected ? "RECEIVING" : "NO_DATA");
        response.put("carlaState", carlaConnected ? "RECEIVING" : "NO_DATA");
        return response;
    }

    /**
     * Detailed engine status with metrics and state summary.
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> response = new LinkedHashMap<>();

        // Service info
        response.put("service", "orchestrator-service (Co-Simulation Engine)");
        response.put("registeredRules", engine.getRegisteredRules());

        // Metrics
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("evaluationCount", engine.getEvaluationCount());
        metrics.put("carlaCommandsSent", engine.getTotalCarlaCommandsSent());
        metrics.put("gamaCommandsSent", engine.getTotalGamaCommandsSent());
        metrics.put("gamaMessagesReceived", stateManager.getGamaMessagesReceived().get());
        metrics.put("carlaMessagesReceived", stateManager.getCarlaMessagesReceived().get());
        response.put("metrics", metrics);

        // Latest state summary
        Map<String, Object> latestState = new LinkedHashMap<>();

        if (stateManager.getGamaState() != null && stateManager.getGamaState().getPayload() != null) {
            var gPayload = stateManager.getGamaState().getPayload();
            Map<String, Object> gamaSummary = new LinkedHashMap<>();
            gamaSummary.put("lastTick", gPayload.getTickNumber());
            gamaSummary.put("agents", gPayload.getAgents() != null ? gPayload.getAgents().size() : 0);
            gamaSummary.put("zones", gPayload.getZones() != null ? gPayload.getZones().size() : 0);
            gamaSummary.put("roads", gPayload.getRoads() != null ? gPayload.getRoads().size() : 0);
            gamaSummary.put("lastUpdate", stateManager.getLastGamaUpdate());
            latestState.put("gama", gamaSummary);
        } else {
            latestState.put("gama", "NO_DATA_YET");
        }

        if (stateManager.getCarlaState() != null && stateManager.getCarlaState().getPayload() != null) {
            var cPayload = stateManager.getCarlaState().getPayload();
            Map<String, Object> carlaSummary = new LinkedHashMap<>();
            carlaSummary.put("lastTick", cPayload.getTickNumber());
            carlaSummary.put("vehicles", cPayload.getNumVehicles());
            carlaSummary.put("mapName", cPayload.getMapName());
            carlaSummary.put("events", cPayload.getEvents() != null ? cPayload.getEvents().size() : 0);
            carlaSummary.put("lastUpdate", stateManager.getLastCarlaUpdate());
            latestState.put("carla", carlaSummary);
        } else {
            latestState.put("carla", "NO_DATA_YET");
        }

        response.put("latestState", latestState);

        return response;
    }
}
