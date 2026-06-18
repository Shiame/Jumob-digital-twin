import { useState } from 'react';
import { BrowserRouter, Routes, Route, NavLink, Navigate } from 'react-router-dom';
import { useWebSocket } from './hooks/useWebSocket';
import { useDemoData } from './hooks/useDemoData';
import OverviewPage from './pages/OverviewPage';
import MapPage from './pages/MapPage';
import AnalyticsPage from './pages/AnalyticsPage';
import EventsPage from './pages/EventsPage';
import './index.css';

function App() {
  const [demoMode, setDemoMode] = useState(true);
  const ws = useWebSocket(demoMode);
  useDemoData(demoMode, ws.processEvent, ws.addEvent);

  // Derive ego vehicle
  const egoVehicle = ws.carlaState.vehicles.find(v => v.role_name === 'hero') || null;

  // Latest active command
  const lastCommand = ws.commands.length > 0 ? ws.commands[0] : null;

  return (
    <BrowserRouter>
      <div className="app-shell">
        {/* ═══ SIDEBAR ═══ */}
        <aside className="sidebar">
          <div className="sidebar-brand">
            <h1>MIDOC</h1>
            <span className="brand-sub">Digital Twin Platform</span>
          </div>

          <nav className="sidebar-nav">
            <NavLink to="/overview" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="3" y="3" width="7" height="7" rx="1" />
                <rect x="14" y="3" width="7" height="7" rx="1" />
                <rect x="3" y="14" width="7" height="7" rx="1" />
                <rect x="14" y="14" width="7" height="7" rx="1" />
              </svg>
              Vue d'ensemble
            </NavLink>

            <NavLink to="/map" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polygon points="1 6 1 22 8 18 16 22 23 18 23 2 16 6 8 2 1 6" />
                <line x1="8" y1="2" x2="8" y2="18" />
                <line x1="16" y1="6" x2="16" y2="22" />
              </svg>
              Carte temps réel
            </NavLink>

            <NavLink to="/analytics" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
              </svg>
              Analytique
            </NavLink>

            <NavLink to="/events" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
                <polyline points="14 2 14 8 20 8" />
                <line x1="16" y1="13" x2="8" y2="13" />
                <line x1="16" y1="17" x2="8" y2="17" />
              </svg>
              Événements
              {ws.events.length > 0 && (
                <span style={{
                  marginLeft: 'auto', fontSize: 10, fontFamily: 'var(--font-mono)',
                  background: 'var(--accent-dim)', color: 'var(--accent)',
                  padding: '1px 6px', borderRadius: 4, fontWeight: 600,
                }}>{ws.events.length}</span>
              )}
            </NavLink>
          </nav>

          <div className="sidebar-footer">
            <div className="connection-indicator">
              <span className={`conn-dot ${ws.connected ? 'online' : (demoMode ? 'online' : 'offline')}`} />
              <span>{ws.connected ? 'Backend connecté' : (demoMode ? 'Mode démo' : 'Déconnecté')}</span>
            </div>
            <button
              className="mode-toggle"
              onClick={() => setDemoMode(prev => !prev)}
            >
              <span className={`mode-dot ${demoMode ? 'demo' : 'live'}`} />
              <span className="mode-label">{demoMode ? 'DEMO' : 'LIVE'}</span>
            </button>
          </div>
        </aside>

        {/* ═══ MAIN ═══ */}
        <main className="main-content">
          <Routes>
            <Route path="/overview" element={
              <OverviewPage
                gamaState={ws.gamaState}
                carlaState={ws.carlaState}
                egoVehicle={egoVehicle}
                lastCommand={lastCommand}
                commands={ws.commands}
                throughput={ws.throughput}
                connected={ws.connected}
                demoMode={demoMode}
                events={ws.events}
              />
            } />
            <Route path="/map" element={
              <MapPage
                gamaState={ws.gamaState}
                carlaState={ws.carlaState}
                egoVehicle={egoVehicle}
                lastCommand={lastCommand}
              />
            } />
            <Route path="/analytics" element={
              <AnalyticsPage
                gamaState={ws.gamaState}
                carlaState={ws.carlaState}
                egoVehicle={egoVehicle}
                commands={ws.commands}
                throughput={ws.throughput}
              />
            } />
            <Route path="/events" element={
              <EventsPage
                events={ws.events}
                commands={ws.commands}
              />
            } />
            <Route path="*" element={<Navigate to="/overview" replace />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}

export default App;
