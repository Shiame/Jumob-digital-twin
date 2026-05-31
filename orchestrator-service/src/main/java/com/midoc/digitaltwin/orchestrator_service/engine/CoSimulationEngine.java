package com.midoc.digitaltwin.orchestrator_service.engine;

import com.midoc.digitaltwin.orchestrator_service.dto.carla.CarlaStateEvent;
import com.midoc.digitaltwin.orchestrator_service.dto.command.CarlaCommand;
import com.midoc.digitaltwin.orchestrator_service.dto.command.GamaCommand;
import com.midoc.digitaltwin.orchestrator_service.dto.gama.GamaStateEvent;
import com.midoc.digitaltwin.orchestrator_service.engine.rules.CoSimRule;
import com.midoc.digitaltwin.orchestrator_service.producer.CommandProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Co-Simulation Engine — The heart of the orchestrator.
 *
 * Applies the Event-Condition-Action (ECA) paradigm:
 *   1. Receives state updates from both simulators (via Kafka consumers)
 *   2. Evaluates all registered co-simulation rules
 *   3. Dispatches resulting commands to the appropriate adapter
 *
 * All CoSimRule beans are auto-injected by Spring, making it trivial
 * to add new scenarios — just create a new @Component implementing CoSimRule.
 */
@Service
public class CoSimulationEngine {

    private static final Logger logger = LoggerFactory.getLogger(CoSimulationEngine.class);

    private final StateManager stateManager;
    private final CommandProducer commandProducer;
    private final List<CoSimRule> rules;

    private final AtomicInteger totalCarlaCommandsSent = new AtomicInteger(0);
    private final AtomicInteger totalGamaCommandsSent = new AtomicInteger(0);
    private final AtomicInteger evaluationCount = new AtomicInteger(0);

    public CoSimulationEngine(StateManager stateManager,
                              CommandProducer commandProducer,
                              List<CoSimRule> rules) {
        this.stateManager = stateManager;
        this.commandProducer = commandProducer;
        this.rules = rules;

        logger.info("═══════════════════════════════════════════════════");
        logger.info("  CO-SIMULATION ENGINE INITIALIZED");
        logger.info("  Registered rules: {}", rules.size());
        for (CoSimRule rule : rules) {
            logger.info("    ✓ {}", rule.getName());
        }
        logger.info("═══════════════════════════════════════════════════");
    }

    /**
     * Called whenever a new GAMA state arrives.
     * Updates the state manager, then evaluates all rules.
     */
    public void onGamaStateReceived(GamaStateEvent event) {
        stateManager.updateGamaState(event);
        evaluateAllRules();
    }

    /**
     * Called whenever a new CARLA state arrives.
     * Updates the state manager, then evaluates all rules.
     */
    public void onCarlaStateReceived(CarlaStateEvent event) {
        stateManager.updateCarlaState(event);
        evaluateAllRules();
    }

    /**
     * Evaluate all registered rules against the current state from both simulators.
     * Any resulting commands are dispatched to the appropriate Kafka topic.
     */
    private void evaluateAllRules() {
        GamaStateEvent gamaState = stateManager.getGamaState();
        CarlaStateEvent carlaState = stateManager.getCarlaState();

        int evalNum = evaluationCount.incrementAndGet();

        for (CoSimRule rule : rules) {
            try {
                // Evaluate for CARLA commands
                List<CarlaCommand> carlaCommands = rule.evaluateForCarla(gamaState, carlaState);
                for (CarlaCommand cmd : carlaCommands) {
                    commandProducer.sendCarlaCommand(cmd);
                    totalCarlaCommandsSent.incrementAndGet();
                    logger.info(" [{}] → carla-commands: {} ({})",
                            rule.getName(), cmd.getCommandType(), cmd.getCommandId());
                }

                // Evaluate for GAMA commands
                List<GamaCommand> gamaCommands = rule.evaluateForGama(gamaState, carlaState);
                for (GamaCommand cmd : gamaCommands) {
                    commandProducer.sendGamaCommand(cmd);
                    totalGamaCommandsSent.incrementAndGet();
                    logger.info(" [{}] → gama-commands: {} ({})",
                            rule.getName(), cmd.getCommandType(), cmd.getCommandId());
                }

            } catch (Exception e) {
                logger.error("Error evaluating rule '{}': {}", rule.getName(), e.getMessage(), e);
            }
        }

        // Periodic summary log
        if (evalNum % 100 == 0) {
            logger.info(" Engine stats — evaluations: {} | carla-cmds sent: {} | gama-cmds sent: {}",
                    evalNum, totalCarlaCommandsSent.get(), totalGamaCommandsSent.get());
        }
    }

    // --- Accessors for REST monitoring ---

    public int getTotalCarlaCommandsSent() {
        return totalCarlaCommandsSent.get();
    }

    public int getTotalGamaCommandsSent() {
        return totalGamaCommandsSent.get();
    }

    public int getEvaluationCount() {
        return evaluationCount.get();
    }

    public List<String> getRegisteredRules() {
        return rules.stream().map(CoSimRule::getName).toList();
    }
}
