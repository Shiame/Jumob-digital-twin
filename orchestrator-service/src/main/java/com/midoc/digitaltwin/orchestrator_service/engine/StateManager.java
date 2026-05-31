package com.midoc.digitaltwin.orchestrator_service.engine;

import com.midoc.digitaltwin.orchestrator_service.dto.carla.CarlaStateEvent;
import com.midoc.digitaltwin.orchestrator_service.dto.gama.GamaStateEvent;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the latest state from both simulators in a thread-safe manner.
 * Acts as the "shared memory" that the rule engine reads from.
 *
 * Both Kafka consumers update this state on each incoming message,
 * and the CoSimulationEngine reads it to evaluate rules.
 */
@Component
@Getter
public class StateManager {

    private static final Logger logger = LoggerFactory.getLogger(StateManager.class);

    private final AtomicReference<GamaStateEvent> latestGamaState = new AtomicReference<>();
    private final AtomicReference<CarlaStateEvent> latestCarlaState = new AtomicReference<>();

    private final AtomicInteger gamaMessagesReceived = new AtomicInteger(0);
    private final AtomicInteger carlaMessagesReceived = new AtomicInteger(0);

    @Getter
    private volatile Instant lastGamaUpdate = null;

    @Getter
    private volatile Instant lastCarlaUpdate = null;

    /**
     * Update the latest GAMA state. Called by the Kafka consumer.
     */
    public void updateGamaState(GamaStateEvent event) {
        latestGamaState.set(event);
        lastGamaUpdate = Instant.now();
        int count = gamaMessagesReceived.incrementAndGet();

        if (count % 50 == 0) {
            logger.info(" GAMA state updated — total received: {} | tick: {}",
                    count, event.getPayload() != null ? event.getPayload().getTickNumber() : "N/A");
        }
    }

    /**
     * Update the latest CARLA state. Called by the Kafka consumer.
     */
    public void updateCarlaState(CarlaStateEvent event) {
        latestCarlaState.set(event);
        lastCarlaUpdate = Instant.now();
        int count = carlaMessagesReceived.incrementAndGet();

        if (count % 50 == 0) {
            logger.info("🚗 CARLA state updated — total received: {} | tick: {}",
                    count, event.getPayload() != null ? event.getPayload().getTickNumber() : "N/A");
        }
    }

    /**
     * Get the latest GAMA state, or null if none received yet.
     */
    public GamaStateEvent getGamaState() {
        return latestGamaState.get();
    }

    /**
     * Get the latest CARLA state, or null if none received yet.
     */
    public CarlaStateEvent getCarlaState() {
        return latestCarlaState.get();
    }
}
