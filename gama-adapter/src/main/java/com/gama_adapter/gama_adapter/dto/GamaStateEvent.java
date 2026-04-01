package com.gama_adapter.gama_adapter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/**
 * Standard event wrapper for all GAMA → Kafka messages.
 * Aligns with the Kafka contracts defined in kafka_contracts.md.
 */
public class GamaStateEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("source")
    private String source;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("payload")
    private GamaPayload payload;

    public GamaStateEvent() {
        this.eventId = "gama-" + UUID.randomUUID().toString().substring(0, 8);
        this.eventType = "TICK_COMPLETED";
        this.source = "GAMA";
        this.timestamp = System.currentTimeMillis();
    }

    // --- Getters & Setters ---

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public GamaPayload getPayload() { return payload; }
    public void setPayload(GamaPayload payload) { this.payload = payload; }

    // --- Inner classes ---

    public static class GamaPayload {
        @JsonProperty("cycle")
        private int cycle;

        @JsonProperty("nbPeople")
        private int nbPeople;

        @JsonProperty("vehicles")
        private List<VehicleData> vehicles;

        public int getCycle() { return cycle; }
        public void setCycle(int cycle) { this.cycle = cycle; }

        public int getNbPeople() { return nbPeople; }
        public void setNbPeople(int nbPeople) { this.nbPeople = nbPeople; }

        public List<VehicleData> getVehicles() { return vehicles; }
        public void setVehicles(List<VehicleData> vehicles) { this.vehicles = vehicles; }
    }

    public static class VehicleData {
        @JsonProperty("id")
        private String id;

        @JsonProperty("x")
        private double x;

        @JsonProperty("y")
        private double y;

        @JsonProperty("speed")
        private double speed;

        @JsonProperty("heading")
        private double heading;

        @JsonProperty("state")
        private String state;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public double getX() { return x; }
        public void setX(double x) { this.x = x; }

        public double getY() { return y; }
        public void setY(double y) { this.y = y; }

        public double getSpeed() { return speed; }
        public void setSpeed(double speed) { this.speed = speed; }

        public double getHeading() { return heading; }
        public void setHeading(double heading) { this.heading = heading; }

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
    }
}
