package com.midoc.digitaltwin.orchestrator_service.dto.command;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Command sent to the GAMA Adapter via Kafka topic 'gama-commands'.
 * The adapter will forward the command to GAMA via WebSocket.
 *
 * Supported commandTypes:
 *   - BLOCK_ROAD        → Block a road segment (payload: roadId, blocked)
 *   - UPDATE_AGENT_STATE → Change an agent's state (payload: agentId, newState)
 *   - PAUSE             → Pause the GAMA simulation
 *   - RESUME            → Resume the GAMA simulation
 *   - STOP              → Stop the GAMA simulation
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GamaCommand {

    @Builder.Default
    private String commandId = "cmd-" + UUID.randomUUID().toString().substring(0, 8);

    private String commandType;

    // --- BLOCK_ROAD ---
    private String roadId;
    private Boolean blocked;

    // --- UPDATE_AGENT_STATE ---
    private String agentId;
    private String newState;

    // --- Context ---
    private String triggeredBy;
}
