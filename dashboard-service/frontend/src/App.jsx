import { useState, useEffect, useCallback } from 'react';
import { BrowserRouter, Routes, Route, NavLink, Navigate } from 'react-router-dom';
import { useWebSocket } from './hooks/useWebSocket';
import { useDemoData } from './hooks/useDemoData';
import OverviewPage from './pages/OverviewPage';
import AnalyticsPage from './pages/AnalyticsPage';
import EventsPage from './pages/EventsPage';
import './index.css';

function App() {
  const [demoMode, setDemoMode] = useState(true);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [isDarkMode, setIsDarkMode] = useState(false);
  const ws = useWebSocket(demoMode);
  useDemoData(demoMode, ws.processEvent, ws.addEvent);

  useEffect(() => {
    if (isDarkMode) {
      document.body.setAttribute('data-theme', 'dark');
    } else {
      document.body.removeAttribute('data-theme');
    }
  }, [isDarkMode]);

  // Hidden toggle: Ctrl+Shift+D to switch modes
  const toggleMode = useCallback(() => {
    ws.setGamaState({
      agents: [], pedestrians: [], vehicles: [],
      roads: [], zones: [],
      cycle: 0, nbPeople: 0, simulationDate: '',
    });
    ws.setCarlaState({
      vehicles: [], events: [],
      tickNumber: 0, mapName: '', numVehicles: 0,
    });
    ws.setCommands([]);
    setDemoMode(prev => !prev);
  }, [ws]);

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.ctrlKey && e.shiftKey && e.key === 'D') {
        e.preventDefault();
        toggleMode();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [toggleMode]);

  const egoVehicle = ws.carlaState.vehicles.find(v => v.role_name === 'hero') || null;
  const lastCommand = ws.commands.length > 0 ? ws.commands[0] : null;

  return (
    <BrowserRouter>
      <div className={`app-shell ${sidebarOpen ? '' : 'sidebar-closed'}`}>
        <aside className="sidebar">
          <div className="sidebar-brand">
            <div className="brand-logo"></div>
            <div className="brand-text">
              <h1>Jumob</h1>
              <span className="brand-sub">Digital Twin Platform</span>
            </div>
          </div>

          <nav className="sidebar-nav">
            <NavLink to="/overview" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="3" y="3" width="7" height="7" rx="1" />
                <rect x="14" y="3" width="7" height="7" rx="1" />
                <rect x="3" y="14" width="7" height="7" rx="1" />
                <rect x="14" y="14" width="7" height="7" rx="1" />
              </svg>
              <span className="nav-text">Vue d'ensemble</span>
            </NavLink>

            <NavLink to="/analytics" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
              </svg>
              <span className="nav-text">Analytique</span>
            </NavLink>

            <NavLink to="/events" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
                <polyline points="14 2 14 8 20 8" />
                <line x1="16" y1="13" x2="8" y2="13" />
                <line x1="16" y1="17" x2="8" y2="17" />
              </svg>
              <span className="nav-text">Événements</span>
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
            <button className="theme-toggle" onClick={() => setIsDarkMode(!isDarkMode)}>
              {isDarkMode ? (
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="5" />
                  <line x1="12" y1="1" x2="12" y2="3" />
                  <line x1="12" y1="21" x2="12" y2="23" />
                  <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
                  <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
                  <line x1="1" y1="12" x2="3" y2="12" />
                  <line x1="21" y1="12" x2="23" y2="12" />
                  <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
                  <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
                </svg>
              ) : (
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
                </svg>
              )}
              <span className="conn-text">{isDarkMode ? 'Mode Clair' : 'Mode Sombre'}</span>
            </button>
            <div className="connection-indicator">
              <span className={`conn-dot ${ws.connected || demoMode ? 'online' : 'offline'}`} />
              <span className="conn-text">{ws.connected ? 'Backend connecté' : (demoMode ? 'Connecté' : 'Déconnecté')}</span>
            </div>
          </div>
        </aside>

        <main className="main-content">
          <button className="sidebar-toggle" onClick={() => setSidebarOpen(!sidebarOpen)}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <line x1="3" y1="12" x2="21" y2="12" />
              <line x1="3" y1="6" x2="21" y2="6" />
              <line x1="3" y1="18" x2="21" y2="18" />
            </svg>
          </button>
          
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
                isDarkMode={isDarkMode}
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
