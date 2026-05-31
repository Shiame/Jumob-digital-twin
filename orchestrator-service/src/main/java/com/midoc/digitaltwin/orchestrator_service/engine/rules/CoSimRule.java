package com.midoc.digitaltwin.orchestrator_service.engine.rules;

import com.midoc.digitaltwin.orchestrator_service.dto.carla.CarlaStateEvent;
import com.midoc.digitaltwin.orchestrator_service.dto.command.CarlaCommand;
import com.midoc.digitaltwin.orchestrator_service.dto.command.GamaCommand;
import com.midoc.digitaltwin.orchestrator_service.dto.gama.GamaStateEvent;

import java.util.List;

/**
 * Interface for a co-simulation rule.
 *
 * Each rule implements the Event-Condition-Action (ECA) pattern:
 *   - Event:     A new state arrives from GAMA or CARLA
 *   - Condition: The rule checks if its trigger condition is met
 *   - Action:    If triggered, the rule produces one or more commands
 *
 * Rules return empty lists when no action is needed.
 */
public interface CoSimRule {

    /** Human-readable name for logging and monitoring. */
    String getName();

    /**
     * Evaluate this rule against the current state from both simulators.
     *
     * @param gamaState  Latest GAMA state (may be null if not yet received)
     * @param carlaState Latest CARLA state (may be null if not yet received)
     * @return List of commands to send to CARLA (may be empty)
     */
    List<CarlaCommand> evaluateForCarla(GamaStateEvent gamaState, CarlaStateEvent carlaState);

    /**
     * Evaluate this rule and produce commands for GAMA.
     *
     * @param gamaState  Latest GAMA state (may be null if not yet received)
     * @param carlaState Latest CARLA state (may be null if not yet received)
     * @return List of commands to send to GAMA (may be empty)
     */
    List<GamaCommand> evaluateForGama(GamaStateEvent gamaState, CarlaStateEvent carlaState);
}
