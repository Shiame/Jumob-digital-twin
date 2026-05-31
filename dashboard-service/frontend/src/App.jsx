import { useState, useEffect, useRef } from 'react';
import './index.css';
import StatusPanel from './components/StatusPanel';
import GamaPanel from './components/GamaPanel';
import CarlaPanel from './components/CarlaPanel';
import RuleEnginePanel from './components/RuleEnginePanel';
import Timeline from './components/Timeline';

function App() {
  const [connected, setConnected] = useState(false);
  
  // State for different components
  const [gamaState, setGamaState] = useState({ pedestrians: 0, vehicles: 0, latestTick: 0 });
  const [carlaState, setCarlaState] = useState({ speed: 0, autopilot: true, latestTick: 0 });
  const [rulesState, setRulesState] = useState({ activeRule: null, targetSpeed: null });
  const [timelineEvents, setTimelineEvents] = useState([]);
  
  const ws = useRef(null);

  useEffect(() => {
    // Connect to WebSocket backend
    const connectWs = () => {
      ws.current = new WebSocket('ws://localhost:8085/ws');
      
      ws.current.onopen = () => {
        setConnected(true);
        addTimelineEvent('SYSTEM', 'Connected to Mission Control backend');
      };
      
      ws.current.onclose = () => {
        setConnected(false);
        setTimeout(connectWs, 3000); // Reconnect loop
      };
      
      ws.current.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          processKafkaEvent(data);
        } catch (e) {
          console.error("Error parsing WS message", e);
        }
      };
    };

    connectWs();
    
    return () => {
      if (ws.current) ws.current.close();
    };
  }, []);

  const addTimelineEvent = (source, message, type = 'info') => {
    setTimelineEvents(prev => [
      { id: Date.now() + Math.random(), time: new Date(), source, message, type },
      ...prev
    ].slice(0, 50)); // Keep last 50 events
  };

  const processKafkaEvent = (event) => {
    const { topic, payload } = event;
    
    if (topic === 'gama-state') {
      // Parse Gama state (assuming payload has agents list or summary)
      const pedestrians = payload.agents ? payload.agents.filter(a => a.type === 'pieton').length : (payload.pedestrians || 0);
      const vehicles = payload.agents ? payload.agents.filter(a => a.type === 'voiture').length : (payload.vehicles || 0);
      
      setGamaState(prev => {
        // Only trigger event if density changed significantly to avoid spam
        if (Math.abs(prev.pedestrians - pedestrians) > 5) {
          // addTimelineEvent('GAMA', `Pedestrian density changed: ${pedestrians}`, 'info');
        }
        return { pedestrians, vehicles, latestTick: payload.tick || 0 };
      });
      
    } else if (topic === 'carla-state') {
      const speed = payload.speed || 0;
      setCarlaState({ speed, autopilot: payload.autopilot !== false, latestTick: payload.tick || 0 });
      
    } else if (topic === 'carla-commands') {
      const cmdType = payload.commandType;
      if (cmdType === 'SET_SPEED_LIMIT') {
        const speed = payload.maxSpeedKmh;
        const rule = payload.triggeredBy || 'UNKNOWN_RULE';
        setRulesState({ activeRule: rule, targetSpeed: speed });
        
        addTimelineEvent('ORCHESTRATOR', `Rule ${rule} triggered! Setting speed to ${speed}km/h`, 'warning');
      }
    }
  };

  return (
    <div className="dashboard-container">
      <header className="header">
        <h1>
          MIDOC DIGITAL TWIN
          <div className="live-badge">
            <span className="live-dot"></span>
            LIVE
          </div>
        </h1>
      </header>

      <div className="grid-layout">
        {/* Left Column - GAMA & Services */}
        <div className="column">
          <StatusPanel connected={connected} />
          <GamaPanel state={gamaState} />
        </div>

        {/* Center Column - Orchestrator & Timeline */}
        <div className="column">
          <RuleEnginePanel state={rulesState} />
          <Timeline events={timelineEvents} />
        </div>

        {/* Right Column - CARLA */}
        <div className="column">
          <CarlaPanel state={carlaState} rules={rulesState} />
        </div>
      </div>
    </div>
  );
}

export default App;
