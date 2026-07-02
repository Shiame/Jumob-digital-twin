import { useEffect, useRef, useCallback } from 'react';

/**
 * Center: Toulouse / MIDOC area (roundabout near Université Paul Sabatier)
 */
const CENTER = { lat: 43.5529, lon: 1.4595 };

// Road network segments (simplified polylines around the area)
const ROAD_SEGMENTS = [
  { id: '2641', points: [[43.5525, 1.4580], [43.5530, 1.4595], [43.5535, 1.4610]] },
  { id: '2642', points: [[43.5535, 1.4610], [43.5540, 1.4625], [43.5538, 1.4640]] },
  { id: '2643', points: [[43.5530, 1.4595], [43.5520, 1.4600], [43.5515, 1.4615]] },
  { id: '2644', points: [[43.5515, 1.4615], [43.5510, 1.4630], [43.5520, 1.4645]] },
  { id: '2645', points: [[43.5540, 1.4570], [43.5535, 1.4580], [43.5525, 1.4580]] },
];

function randomInRange(min, max) {
  return min + Math.random() * (max - min);
}

function lerpPos(points, t) {
  const totalSegs = points.length - 1;
  const segIdx = Math.min(Math.floor(t * totalSegs), totalSegs - 1);
  const segT = (t * totalSegs) - segIdx;
  const a = points[segIdx];
  const b = points[segIdx + 1];
  return [a[0] + (b[0] - a[0]) * segT, a[1] + (b[1] - a[1]) * segT];
}

/** Pre-generate a set of agents that walk along roads */
function generateAgents(count, type) {
  return Array.from({ length: count }, (_, i) => {
    const road = ROAD_SEGMENTS[Math.floor(Math.random() * ROAD_SEGMENTS.length)];
    const progress = Math.random();
    const speed = type === 'pedestrian' ? randomInRange(1, 5) : randomInRange(20, 50);
    const direction = Math.random() > 0.5 ? 1 : -1;
    return {
      id: `${type}_${i}`,
      type,
      road,
      progress,
      speed,
      direction,
      heading: Math.random() * 360,
      state: type === 'pedestrian' ? 'WALKING' : 'DRIVING',
    };
  });
}

/**
 * Demo data generator: produces realistic fake data that mimics
 * real Kafka events for presentation purposes.
 */
export function useDemoData(active, processEvent, addEvent) {
  const agents = useRef(null);
  const egoState = useRef({
    lat: CENTER.lat + 0.001,
    lon: CENTER.lon - 0.001,
    speed: 35,
    heading: 90,
    progress: 0,
    routeIdx: 0,
  });
  const tickRef = useRef(0);
  const scenarioTimer = useRef(0);
  const lastCollisionTime = useRef(0);
  const blockedRoad = useRef(null);

  // Initialize agents once
  if (!agents.current) {
    agents.current = {
      pedestrians: generateAgents(40, 'pedestrian'),
      cars: generateAgents(12, 'car'),
      buses: generateAgents(3, 'bus'),
      bikes: generateAgents(8, 'bike'),
    };
  }

  const tick = useCallback(() => {
    tickRef.current++;
    scenarioTimer.current++;
    const t = tickRef.current;

    // ── Move agents along their roads ──
    const allAgents = [];
    Object.values(agents.current).forEach(group => {
      group.forEach(agent => {
        const dt = agent.type === 'pedestrian' ? 0.003 : 0.008;
        agent.progress += dt * agent.direction;

        // Bounce at endpoints
        if (agent.progress > 1) { agent.progress = 1; agent.direction = -1; }
        if (agent.progress < 0) { agent.progress = 0; agent.direction = 1; }

        // Add some random lateral offset for realism
        const [lat, lon] = lerpPos(agent.road.points, agent.progress);
        const offset = (Math.sin(t * 0.1 + parseInt(agent.id.split('_')[1])) * 0.0002);

        allAgents.push({
          id: agent.id,
          type: agent.type,
          x: lon + offset,
          y: lat + offset * 0.7,
          speed: agent.speed + Math.sin(t * 0.05) * 2,
          heading: agent.heading + Math.sin(t * 0.02) * 5,
          acceleration: Math.sin(t * 0.03) * 0.5,
          roadId: agent.road.id,
          state: agent.state,
        });
      });
    });

    const allPedestrians = allAgents.filter(a => a.type === 'pedestrian');
    // Vary the number of visible pedestrians dynamically so it's not a flat line
    const visiblePedCount = Math.floor(25 + Math.sin(t * 0.04) * 12);
    const pedestrianAgents = allPedestrians.slice(0, visiblePedCount);

    const vehicleAgents = allAgents.filter(a => a.type !== 'pedestrian');

    // ── Move ego vehicle ──
    const ego = egoState.current;
    const egoRoute = ROAD_SEGMENTS[ego.routeIdx % ROAD_SEGMENTS.length];
    ego.progress += 0.005;
    if (ego.progress > 1) {
      ego.progress = 0;
      ego.routeIdx++;
    }
    const [egoLat, egoLon] = lerpPos(egoRoute.points, ego.progress);
    ego.lat = egoLat;
    ego.lon = egoLon;

    // Vary speed based on scenario triggers
    const baseSpeed = 40;
    const isSlowdown = scenarioTimer.current > 60 && scenarioTimer.current < 90;
    ego.speed = isSlowdown
      ? 15 + Math.sin(t * 0.1) * 3
      : baseSpeed + Math.sin(t * 0.05) * 8;

    // ── Emit GAMA state ──
    processEvent({
      topic: 'gama-state',
      payload: {
        cycle: t,
        tickNumber: t,
        simulationDate: new Date().toISOString(),
        nbPeople: pedestrianAgents.length,
        agents: allAgents,
        pedestrians: pedestrianAgents,
        vehicles: vehicleAgents,
        roads: ROAD_SEGMENTS.map(r => ({
          roadId: r.id,
          congestionLevel: (blockedRoad.current === r.id) ? 0.95 : 0.2 + Math.sin(t * 0.01 + parseInt(r.id)) * 0.3,
          speedCoeff: (blockedRoad.current === r.id) ? 0 : 0.8 + Math.random() * 0.2,
          blocked: blockedRoad.current === r.id,
          movingAgents: {},
        })),
        zones: [
          { zoneId: 'zone_center', pedestrianCount: Math.floor(12 + Math.sin(t * 0.02) * 8), pollutionLevel: 0.35 + Math.sin(t * 0.01) * 0.15 },
          { zoneId: 'zone_north', pedestrianCount: Math.floor(6 + Math.sin(t * 0.03) * 4), pollutionLevel: 0.2 + Math.sin(t * 0.015) * 0.1 },
        ],
      },
    });

    // ── Emit CARLA state ──
    const carlaEvents = [];
    if (scenarioTimer.current === 10) {
      carlaEvents.push({
        type: 'COLLISION',
        position: { x: ego.lon * 100, y: ego.lat * 100, latitude: ego.lat, longitude: ego.lon },
        otherActorType: 'vehicle',
        severity: 'high',
      });
      lastCollisionTime.current = t;
      blockedRoad.current = '2642';
      
      // Simulate orchestrator reaction
      setTimeout(() => {
        processEvent({
          topic: 'carla-commands',
          payload: {
            commandType: 'BLOCK_ROAD',
            roadId: '2642',
            triggeredBy: 'S2_COLLISION_BLOCK',
            reason: 'COLLISION_DETECTED',
          },
        });
      }, 500);
    }

    processEvent({
      topic: 'carla-state',
      payload: {
        tick_number: t,
        map_name: 'Town05',
        num_vehicles: 8,
        vehicles: [
          {
            id: 1, type: 'vehicle.tesla.model3',
            x: ego.lon * 100, y: ego.lat * 100, z: 0,
            latitude: ego.lat, longitude: ego.lon,
            speed_kmh: ego.speed,
            vx: ego.speed / 3.6, vy: 0, vz: 0,
            pitch: 0, yaw: ego.heading, roll: 0,
            role_name: 'hero',
          },
          ...Array.from({ length: 7 }, (_, i) => ({
            id: i + 2, type: 'vehicle.audi.a2',
            x: 0, y: 0, z: 0,
            latitude: CENTER.lat + (Math.random() - 0.5) * 0.005,
            longitude: CENTER.lon + (Math.random() - 0.5) * 0.005,
            speed_kmh: 20 + Math.random() * 30,
            vx: 0, vy: 0, vz: 0,
            pitch: 0, yaw: Math.random() * 360, roll: 0,
            role_name: 'npc',
          })),
        ],
        events: carlaEvents,
      },
    });

    // Emulate network jitter for Kafka throughput chart
    const jitter = Math.floor(Math.random() * 6);
    for (let i = 0; i < jitter; i++) {
      // Just increment the counter in useWebSocket without changing state
      processEvent({ topic: 'gama-state', payload: { _dummy: true } });
    }

    // ── Trigger S1 scenario periodically ──
    if (scenarioTimer.current === 60) {
      processEvent({
        topic: 'carla-commands',
        payload: {
          commandType: 'SET_SPEED_LIMIT',
          maxSpeedKmh: 20,
          reason: 'HIGH_PEDESTRIAN_DENSITY',
          triggeredBy: 'S1_PEDESTRIAN_DENSITY_GEO',
          alignmentMethod: 'GEO_PROXIMITY',
          pedestrianCount: 14,
          radiusMeters: 50,
          egoLatitude: ego.lat,
          egoLongitude: ego.lon,
        },
      });
    }

    if (scenarioTimer.current >= 100) {
      scenarioTimer.current = 0;
    }
    
    // Unblock road after a while
    if (t - lastCollisionTime.current > 40 && blockedRoad.current) {
      blockedRoad.current = null;
    }
  }, [processEvent]);

  useEffect(() => {
    if (!active) return;

    const interval = setInterval(tick, 500); // 2 Hz for demo
    return () => clearInterval(interval);
  }, [active, tick, addEvent]);
}
