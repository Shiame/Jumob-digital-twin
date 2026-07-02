import { useState, useEffect, useRef, useCallback } from 'react';

const WS_URL = 'ws://localhost:8085/ws';
const RECONNECT_DELAY = 3000;

/**
 * Core WebSocket hook — connects to the dashboard backend and
 * dispatches incoming Kafka events to the appropriate state slices.
 */
export function useWebSocket(demoMode) {
  const [connected, setConnected] = useState(false);
  const [gamaState, setGamaState] = useState({
    agents: [], pedestrians: [], vehicles: [],
    roads: [], zones: [],
    cycle: 0, nbPeople: 0, simulationDate: '',
  });
  const [carlaState, setCarlaState] = useState({
    vehicles: [], events: [],
    tickNumber: 0, mapName: '', numVehicles: 0,
  });
  const [commands, setCommands] = useState([]);
  const [events, setEvents] = useState([]);
  const [throughput, setThroughput] = useState({ gama: 0, carla: 0, commands: 0 });

  const ws = useRef(null);
  const msgCount = useRef({ gama: 0, carla: 0, commands: 0 });
  const throughputInterval = useRef(null);

  const addEvent = useCallback((source, message, type = 'info') => {
    setEvents(prev => [
      { id: Date.now() + Math.random(), time: new Date(), source, message, type },
      ...prev,
    ].slice(0, 200));
  }, []);

  // Throughput tracking (msgs/sec)
  useEffect(() => {
    throughputInterval.current = setInterval(() => {
      setThroughput({
        gama: msgCount.current.gama,
        carla: msgCount.current.carla,
        commands: msgCount.current.commands,
      });
      msgCount.current = { gama: 0, carla: 0, commands: 0 };
    }, 1000);
    return () => clearInterval(throughputInterval.current);
  }, []);

  // WebSocket connection (only when NOT in demo mode)
  useEffect(() => {
    if (demoMode) {
      setConnected(false);
      return;
    }

    let reconnectTimer;
    const connect = () => {
      ws.current = new WebSocket(WS_URL);

      ws.current.onopen = () => {
        setConnected(true);
      };

      ws.current.onclose = () => {
        setConnected(false);
        reconnectTimer = setTimeout(connect, RECONNECT_DELAY);
      };

      ws.current.onerror = () => {
        ws.current?.close();
      };

      ws.current.onmessage = (evt) => {
        try {
          const data = JSON.parse(evt.data);
          processEvent(data);
        } catch (e) {
          console.error('WS parse error', e);
        }
      };
    };

    connect();
    return () => {
      clearTimeout(reconnectTimer);
      if (ws.current) ws.current.close();
    };
  }, [demoMode]);

  const processEvent = useCallback((event) => {
    const { topic, payload } = event;

    if (topic === 'gama-state') {
      msgCount.current.gama++;
      if (payload._dummy) return;
      
      setGamaState({
        agents: payload.agents || [],
        pedestrians: payload.pedestrians || [],
        vehicles: payload.vehicles || [],
        roads: payload.roads || [],
        zones: payload.zones || [],
        cycle: payload.cycle || payload.tickNumber || 0,
        nbPeople: payload.nbPeople || 0,
        simulationDate: payload.simulationDate || '',
      });
    } else if (topic === 'carla-state') {
      msgCount.current.carla++;
      setCarlaState({
        vehicles: payload.vehicles || [],
        events: payload.events || [],
        tickNumber: payload.tick_number || 0,
        mapName: payload.map_name || '',
        numVehicles: payload.num_vehicles || 0,
      });

      // Log collisions
      if (payload.events) {
        payload.events.forEach(ev => {
          if (ev.type === 'COLLISION') {
            addEvent('CARLA', `Collision détectée — sévérité: ${ev.severity || 'N/A'}`, 'error');
          }
        });
      }
    } else if (topic === 'carla-commands') {
      msgCount.current.commands++;
      setCommands(prev => [
        { ...payload, receivedAt: new Date() },
        ...prev,
      ].slice(0, 100));

      const cmd = payload.commandType;
      const rule = payload.triggeredBy || 'unknown';
      if (cmd === 'SET_SPEED_LIMIT') {
        addEvent('ORCHESTRATOR', `Règle ${rule} → Limite vitesse ${payload.maxSpeedKmh} km/h (${payload.reason})`, 'warning');
      } else if (cmd === 'BLOCK_ROAD') {
        addEvent('ORCHESTRATOR', `Règle ${rule} → Blocage route ${payload.roadId}`, 'error');
      } else {
        addEvent('ORCHESTRATOR', `Commande ${cmd} par ${rule}`, 'info');
      }
    }
  }, [addEvent]);

  return {
    connected, gamaState, carlaState, commands, events, throughput,
    addEvent, processEvent,
    setGamaState, setCarlaState, setCommands,
  };
}
