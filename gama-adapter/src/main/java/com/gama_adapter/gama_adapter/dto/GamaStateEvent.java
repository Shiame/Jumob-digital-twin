package com.gama_adapter.gama_adapter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Standard event wrapper for all GAMA → Kafka messages.
 *
 * Updated to support the full Bruno MobilitySimulator model output:
 *   - movingAgentStatusesByStep  → vehicles + pedestrians with passengers, roadId
 *   - trafficSignalsStatusesByStep → traffic lights (green/red) with GPS
 *   - roadsStatusesByStep           → road occupation score + agent list
 *   - busStopsStatusesByStep        → waiting pedestrians per stop
 *   - parkingsStatusesByStep        → parked vehicle counts
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    // ═══════════════════════════════════════════════════════════════
    //  Payload — top-level container
    // ═══════════════════════════════════════════════════════════════

    public static class GamaPayload {

        @JsonProperty("cycle")
        private int cycle;

        @JsonProperty("tickNumber")
        private int tickNumber;

        @JsonProperty("experimentId")
        private String experimentId;

        @JsonProperty("fragmentId")
        private int fragmentId;

        @JsonProperty("simulationDate")
        private String simulationDate;

        @JsonProperty("nbPeople")
        private int nbPeople;

        /** Moving agents: cars, motorcycles, trucks, bikes */
        @JsonProperty("vehicles")
        private List<VehicleData> vehicles = new ArrayList<>();

        /** Pedestrians — separated for density calculation */
        @JsonProperty("pedestrians")
        private List<VehicleData> pedestrians = new ArrayList<>();

        /** Backward compat alias = vehicles + pedestrians */
        @JsonProperty("agents")
        private List<VehicleData> agents = new ArrayList<>();

        /** Traffic light states per intersection — used to sync CARLA signals */
        @JsonProperty("trafficSignals")
        private List<TrafficSignalData> trafficSignals = new ArrayList<>();

        /** Per-road congestion and occupation data */
        @JsonProperty("roads")
        private List<RoadData> roads = new ArrayList<>();

        /** Bus/transport stop occupancy — waiting pedestrians count */
        @JsonProperty("busStops")
        private List<BusStopData> busStops = new ArrayList<>();

        /** Parking capacity & occupancy — used to redirect CARLA vehicles */
        @JsonProperty("parkings")
        private List<ParkingData> parkings = new ArrayList<>();

        /** Legacy zone-based data (pedestrian density + pollution) */
        @JsonProperty("zones")
        private List<ZoneData> zones = new ArrayList<>();

        // --- Getters & Setters ---

        public int getCycle() { return cycle; }
        public void setCycle(int cycle) { this.cycle = cycle; this.tickNumber = cycle; }

        public int getTickNumber() { return tickNumber; }
        public void setTickNumber(int tickNumber) { this.tickNumber = tickNumber; }

        public String getExperimentId() { return experimentId; }
        public void setExperimentId(String experimentId) { this.experimentId = experimentId; }

        public int getFragmentId() { return fragmentId; }
        public void setFragmentId(int fragmentId) { this.fragmentId = fragmentId; }

        public String getSimulationDate() { return simulationDate; }
        public void setSimulationDate(String simulationDate) { this.simulationDate = simulationDate; }

        public int getNbPeople() { return nbPeople; }
        public void setNbPeople(int nbPeople) { this.nbPeople = nbPeople; }

        public List<VehicleData> getVehicles() { return vehicles; }
        public void setVehicles(List<VehicleData> vehicles) { this.vehicles = vehicles; }

        public List<VehicleData> getPedestrians() { return pedestrians; }
        public void setPedestrians(List<VehicleData> pedestrians) { this.pedestrians = pedestrians; }

        public List<VehicleData> getAgents() { return agents; }
        public void setAgents(List<VehicleData> agents) { this.agents = agents; }

        public List<TrafficSignalData> getTrafficSignals() { return trafficSignals; }
        public void setTrafficSignals(List<TrafficSignalData> trafficSignals) { this.trafficSignals = trafficSignals; }

        public List<RoadData> getRoads() { return roads; }
        public void setRoads(List<RoadData> roads) { this.roads = roads; }

        public List<BusStopData> getBusStops() { return busStops; }
        public void setBusStops(List<BusStopData> busStops) { this.busStops = busStops; }

        public List<ParkingData> getParkings() { return parkings; }
        public void setParkings(List<ParkingData> parkings) { this.parkings = parkings; }

        public List<ZoneData> getZones() { return zones; }
        public void setZones(List<ZoneData> zones) { this.zones = zones; }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Vehicle / Pedestrian agent
    // ═══════════════════════════════════════════════════════════════

    public static class VehicleData {

        @JsonProperty("id")
        private String id;

        @JsonProperty("type")
        private String type = "car";

        /** GPS longitude (EPSG:4326) */
        @JsonProperty("x")
        private double x;

        /** GPS latitude (EPSG:4326) */
        @JsonProperty("y")
        private double y;

        @JsonProperty("speed")
        private double speed;

        @JsonProperty("heading")
        private double heading;

        @JsonProperty("acceleration")
        private double acceleration;

        @JsonProperty("roadId")
        private String roadId;

        @JsonProperty("sourceIntersectionId")
        private String sourceIntersectionId;

        @JsonProperty("targetIntersectionId")
        private String targetIntersectionId;

        /** IDs of pedestrian passengers currently on board (for buses/cars) */
        @JsonProperty("passengers")
        private List<String> passengers = new ArrayList<>();

        @JsonProperty("numberOfPassengers")
        private int numberOfPassengers;

        @JsonProperty("currentLane")
        private int currentLane;

        @JsonProperty("length")
        private double length;

        @JsonProperty("width")
        private double width;

        /** State machine state: DRIVING, WAITING_TRANSPORT, ON_TRANSPORT, etc. */
        @JsonProperty("state")
        private String state;

        /** Host vehicle id (for pedestrians ON a bus) */
        @JsonProperty("hostId")
        private String hostId;

        @JsonProperty("hostType")
        private String hostType;

        @JsonProperty("simulationStep")
        private int simulationStep;

        // --- Getters & Setters ---
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        public double getSpeed() { return speed; }
        public void setSpeed(double speed) { this.speed = speed; }
        public double getHeading() { return heading; }
        public void setHeading(double heading) { this.heading = heading; }
        public double getAcceleration() { return acceleration; }
        public void setAcceleration(double acceleration) { this.acceleration = acceleration; }
        public String getRoadId() { return roadId; }
        public void setRoadId(String roadId) { this.roadId = roadId; }
        public String getSourceIntersectionId() { return sourceIntersectionId; }
        public void setSourceIntersectionId(String id) { this.sourceIntersectionId = id; }
        public String getTargetIntersectionId() { return targetIntersectionId; }
        public void setTargetIntersectionId(String id) { this.targetIntersectionId = id; }
        public List<String> getPassengers() { return passengers; }
        public void setPassengers(List<String> passengers) { this.passengers = passengers; }
        public int getNumberOfPassengers() { return numberOfPassengers; }
        public void setNumberOfPassengers(int n) { this.numberOfPassengers = n; }
        public int getCurrentLane() { return currentLane; }
        public void setCurrentLane(int currentLane) { this.currentLane = currentLane; }
        public double getLength() { return length; }
        public void setLength(double length) { this.length = length; }
        public double getWidth() { return width; }
        public void setWidth(double width) { this.width = width; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getHostId() { return hostId; }
        public void setHostId(String hostId) { this.hostId = hostId; }
        public String getHostType() { return hostType; }
        public void setHostType(String hostType) { this.hostType = hostType; }
        public int getSimulationStep() { return simulationStep; }
        public void setSimulationStep(int simulationStep) { this.simulationStep = simulationStep; }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Traffic signal — drives CARLA traffic light sync
    // ═══════════════════════════════════════════════════════════════

    public static class TrafficSignalData {

        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        /** "green" or "red" */
        @JsonProperty("state")
        private String state;

        /** GPS longitude */
        @JsonProperty("lon")
        private double lon;

        /** GPS latitude */
        @JsonProperty("lat")
        private double lat;

        @JsonProperty("simulationStep")
        private int simulationStep;

        // --- Getters & Setters ---
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public double getLon() { return lon; }
        public void setLon(double lon) { this.lon = lon; }
        public double getLat() { return lat; }
        public void setLat(double lat) { this.lat = lat; }
        public int getSimulationStep() { return simulationStep; }
        public void setSimulationStep(int simulationStep) { this.simulationStep = simulationStep; }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Road — congestion + occupation score
    // ═══════════════════════════════════════════════════════════════

    public static class RoadData {

        @JsonProperty("roadId")
        private String roadId;

        @JsonProperty("osmId")
        private String osmId;

        /** Score in [0,1]: how occupied the road is (from Bruno's model) */
        @JsonProperty("occupationScore")
        private double occupationScore;

        /**
         * Estimated pollution score derived from occupation + agent count.
         * Calculated by the transformer (not provided directly by GAMA).
         */
        @JsonProperty("pollutionScore")
        private double pollutionScore;

        @JsonProperty("congestionLevel")
        private double congestionLevel;

        @JsonProperty("speedCoeff")
        private double speedCoeff = 1.0;

        @JsonProperty("blocked")
        private boolean blocked = false;

        /** Map of agentId → agentType currently on this road */
        @JsonProperty("movingAgents")
        private Map<String, String> movingAgents;

        @JsonProperty("simulationStep")
        private int simulationStep;

        // --- Getters & Setters ---
        public String getRoadId() { return roadId; }
        public void setRoadId(String roadId) { this.roadId = roadId; }
        public String getOsmId() { return osmId; }
        public void setOsmId(String osmId) { this.osmId = osmId; }
        public double getOccupationScore() { return occupationScore; }
        public void setOccupationScore(double occupationScore) { this.occupationScore = occupationScore; }
        public double getPollutionScore() { return pollutionScore; }
        public void setPollutionScore(double pollutionScore) { this.pollutionScore = pollutionScore; }
        public double getCongestionLevel() { return congestionLevel; }
        public void setCongestionLevel(double congestionLevel) { this.congestionLevel = congestionLevel; }
        public double getSpeedCoeff() { return speedCoeff; }
        public void setSpeedCoeff(double speedCoeff) { this.speedCoeff = speedCoeff; }
        public boolean isBlocked() { return blocked; }
        public void setBlocked(boolean blocked) { this.blocked = blocked; }
        public Map<String, String> getMovingAgents() { return movingAgents; }
        public void setMovingAgents(Map<String, String> movingAgents) { this.movingAgents = movingAgents; }
        public int getSimulationStep() { return simulationStep; }
        public void setSimulationStep(int simulationStep) { this.simulationStep = simulationStep; }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Bus stop — pedestrian density at stops
    // ═══════════════════════════════════════════════════════════════

    public static class BusStopData {

        @JsonProperty("stopId")
        private String stopId;

        /** Number of pedestrians waiting — used for density scenario */
        @JsonProperty("waitingCount")
        private int waitingCount;

        /** List of waiting pedestrian IDs */
        @JsonProperty("waitingPedestrians")
        private List<String> waitingPedestrians = new ArrayList<>();

        @JsonProperty("simulationStep")
        private int simulationStep;

        // --- Getters & Setters ---
        public String getStopId() { return stopId; }
        public void setStopId(String stopId) { this.stopId = stopId; }
        public int getWaitingCount() { return waitingCount; }
        public void setWaitingCount(int waitingCount) { this.waitingCount = waitingCount; }
        public List<String> getWaitingPedestrians() { return waitingPedestrians; }
        public void setWaitingPedestrians(List<String> wp) {
            this.waitingPedestrians = wp;
            this.waitingCount = wp.size();
        }
        public int getSimulationStep() { return simulationStep; }
        public void setSimulationStep(int simulationStep) { this.simulationStep = simulationStep; }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Parking — used to redirect CARLA vehicles when congested
    // ═══════════════════════════════════════════════════════════════

    public static class ParkingData {

        @JsonProperty("parkingId")
        private String parkingId;

        /** Current number of parked vehicles */
        @JsonProperty("parkedVehicles")
        private int parkedVehicles;

        /** True if parking is full (to be calculated by transformer) */
        @JsonProperty("isFull")
        private boolean isFull = false;

        @JsonProperty("simulationStep")
        private int simulationStep;

        // --- Getters & Setters ---
        public String getParkingId() { return parkingId; }
        public void setParkingId(String parkingId) { this.parkingId = parkingId; }
        public int getParkedVehicles() { return parkedVehicles; }
        public void setParkedVehicles(int parkedVehicles) { this.parkedVehicles = parkedVehicles; }
        public boolean isFull() { return isFull; }
        public void setFull(boolean full) { isFull = full; }
        public int getSimulationStep() { return simulationStep; }
        public void setSimulationStep(int simulationStep) { this.simulationStep = simulationStep; }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Zone — legacy pedestrian density + pollution (backward compat)
    // ═══════════════════════════════════════════════════════════════

    public static class ZoneData {

        @JsonProperty("zoneId")
        private String zoneId;

        @JsonProperty("pedestrianCount")
        private int pedestrianCount;

        @JsonProperty("pollutionLevel")
        private double pollutionLevel;

        public String getZoneId() { return zoneId; }
        public void setZoneId(String zoneId) { this.zoneId = zoneId; }
        public int getPedestrianCount() { return pedestrianCount; }
        public void setPedestrianCount(int pedestrianCount) { this.pedestrianCount = pedestrianCount; }
        public double getPollutionLevel() { return pollutionLevel; }
        public void setPollutionLevel(double pollutionLevel) { this.pollutionLevel = pollutionLevel; }
    }
}
