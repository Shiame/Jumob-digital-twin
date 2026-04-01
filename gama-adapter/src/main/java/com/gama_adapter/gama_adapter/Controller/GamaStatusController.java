package com.gama_adapter.gama_adapter.Controller;

import com.gama_adapter.gama_adapter.Service.GamaWebSocketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST endpoints for monitoring the Gama Adapter status.
 */
@RestController
@RequestMapping("/api")
public class GamaStatusController {

    private final GamaWebSocketService gamaWebSocketService;

    public GamaStatusController(GamaWebSocketService gamaWebSocketService) {
        this.gamaWebSocketService = gamaWebSocketService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
            "service", "gama-adapter",
            "gamaConnection", gamaWebSocketService.getConnectionStatus(),
            "experimentId", gamaWebSocketService.getExperimentId() != null ? gamaWebSocketService.getExperimentId() : "N/A",
            "messagesForwarded", gamaWebSocketService.getMessagesForwarded(),
            "lastMessageTime", gamaWebSocketService.getLastMessageTime()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        String status = gamaWebSocketService.getConnectionStatus();
        boolean isHealthy = !"DISCONNECTED".equals(status) && !"FAILED".equals(status);
        return ResponseEntity.ok(Map.of(
            "status", isHealthy ? "UP" : "DOWN",
            "gamaConnection", status
        ));
    }
}
