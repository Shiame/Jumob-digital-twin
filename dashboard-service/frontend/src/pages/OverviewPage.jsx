import React, { useMemo } from 'react';
import TopologyDiagram from '../components/TopologyDiagram';
import ScenarioPanel from '../components/ScenarioPanel';

export default function OverviewPage({
  gamaState, carlaState, egoVehicle, lastCommand,
  commands, throughput, connected, demoMode, events,
}) {
  const egoSpeed = egoVehicle?.speed_kmh ?? 0;
  const isSlowdown = lastCommand?.commandType === 'SET_SPEED_LIMIT' && egoSpeed <= (lastCommand.maxSpeedKmh || 999) * 1.5;

  const collisionCount = useMemo(() =>
    events.filter(e => e.type === 'error' && e.source === 'CARLA').length,
  [events]);

  const commandCount = commands.length;

  // Congestion average
  const avgCongestion = useMemo(() => {
    const roads = gamaState.roads;
    if (!roads.length) return 0;
    return roads.reduce((s, r) => s + (r.congestionLevel || 0), 0) / roads.length;
  }, [gamaState.roads]);

  return (
    <>
      <div className="page-header">
        <span className="page-title">Vue d'ensemble</span>
        <div className="page-meta">
          <div className="meta-item">
            <span>Cycle GAMA</span>
            <span className="meta-value">{gamaState.cycle}</span>
          </div>
          <div className="meta-item">
            <span>Tick CARLA</span>
            <span className="meta-value">{carlaState.tickNumber}</span>
          </div>
          <div className="meta-item">
            <span>Mode</span>
            <span className="meta-value" style={{ color: demoMode ? 'var(--amber)' : 'var(--green)' }}>
              {demoMode ? 'DEMO' : 'LIVE'}
            </span>
          </div>
        </div>
      </div>

      <div className="page-body" style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        {/* Row 1: KPI Strip */}
        <div className="stat-grid cols-4">
          <StatBlock
            label="Agents GAMA"
            value={gamaState.agents.length || gamaState.nbPeople}
            sub={`${(gamaState.pedestrians?.length || 0)} piétons · ${(gamaState.vehicles?.length || 0)} véhicules`}
            color="var(--accent)"
          />
          <StatBlock
            label="Véhicule Ego"
            value={egoSpeed.toFixed(1)}
            unit="km/h"
            color={isSlowdown ? 'var(--amber)' : 'var(--green)'}
            status={isSlowdown ? 'RALENTI' : 'NORMAL'}
            statusColor={isSlowdown ? 'warn' : 'ok'}
          />
          <StatBlock
            label="Congestion moy."
            value={(avgCongestion * 100).toFixed(0)}
            unit="%"
            color={avgCongestion > 0.5 ? 'var(--red)' : avgCongestion > 0.3 ? 'var(--amber)' : 'var(--green)'}
          />
          <StatBlock
            label="Commandes émises"
            value={commandCount}
            sub={`${collisionCount} collision(s)`}
            color="var(--purple)"
          />
        </div>

        {/* Row 2: Topology + Scenarios */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
          {/* System Topology */}
          <div className="card">
            <div className="card-header">
              <span className="card-title">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="3" />
                  <path d="M12 1v4M12 19v4M4.22 4.22l2.83 2.83M16.95 16.95l2.83 2.83M1 12h4M19 12h4M4.22 19.78l2.83-2.83M16.95 7.05l2.83-2.83" />
                </svg>
                Topologie système
              </span>
              <span className="card-badge" style={{
                background: connected || demoMode ? 'var(--green-dim)' : 'var(--red-dim)',
                color: connected || demoMode ? 'var(--green)' : 'var(--red)',
              }}>
                {connected || demoMode ? 'OPÉRATIONNEL' : 'DÉCONNECTÉ'}
              </span>
            </div>
            <div className="card-body">
              <TopologyDiagram throughput={throughput} connected={connected || demoMode} />
            </div>
          </div>

          {/* Scenarios */}
          <div className="card">
            <div className="card-header">
              <span className="card-title">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <polygon points="12 2 2 7 12 12 22 7 12 2" />
                  <polyline points="2 17 12 22 22 17" />
                  <polyline points="2 12 12 17 22 12" />
                </svg>
                Scénarios co-simulation
              </span>
            </div>
            <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <ScenarioPanel commands={commands} lastCommand={lastCommand} events={events} />
            </div>
          </div>
        </div>

        {/* Row 3: Services Status + Recent Events */}
        <div style={{ display: 'grid', gridTemplateColumns: '340px 1fr', gap: 20 }}>
          {/* Services */}
          <div className="card">
            <div className="card-header">
              <span className="card-title">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="2" y="2" width="20" height="8" rx="2" />
                  <rect x="2" y="14" width="20" height="8" rx="2" />
                  <circle cx="6" cy="6" r="1" />
                  <circle cx="6" cy="18" r="1" />
                </svg>
                Services
              </span>
            </div>
            <div className="card-body">
              <ServiceRow name="Kafka Broker" status={connected || demoMode} detail={`${throughput.gama + throughput.carla} msg/s`} />
              <ServiceRow name="GAMA Adapter" status={gamaState.cycle > 0} detail={`cycle ${gamaState.cycle}`} />
              <ServiceRow name="CARLA Adapter" status={carlaState.tickNumber > 0} detail={`tick ${carlaState.tickNumber}`} />
              <ServiceRow name="Orchestrateur" status={connected || demoMode} detail={`${commandCount} cmds`} />
              <ServiceRow name="Dashboard Backend" status={connected || demoMode} detail="ws://8085" />
            </div>
          </div>

          {/* Recent events compact */}
          <div className="card">
            <div className="card-header">
              <span className="card-title">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="10" />
                  <polyline points="12 6 12 12 16 14" />
                </svg>
                Activité récente
              </span>
              <span className="card-badge" style={{ background: 'var(--accent-dim)', color: 'var(--accent)' }}>
                {events.length} events
              </span>
            </div>
            <div className="card-body" style={{ maxHeight: 200, overflowY: 'auto' }}>
              {events.slice(0, 8).map((ev, i) => (
                <div key={ev.id} className="event-item" style={{ padding: '6px 0', animation: i === 0 ? 'slide-up 0.2s ease-out' : 'none' }}>
                  <div className="event-dot" style={{ background: getSourceColor(ev.source), marginTop: 4 }} />
                  <div className="event-content" style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span className="event-source" style={{
                        background: getSourceBg(ev.source),
                        color: getSourceColor(ev.source),
                      }}>{ev.source}</span>
                      <span className="event-time">
                        {ev.time instanceof Date ? ev.time.toLocaleTimeString([], { hour12: false }) : ''}
                      </span>
                    </div>
                    <div className="event-message" style={{ fontSize: 11, marginTop: 2 }}>{ev.message}</div>
                  </div>
                </div>
              ))}
              {events.length === 0 && (
                <div style={{ color: 'var(--text-muted)', fontSize: 12, textAlign: 'center', padding: 20, fontStyle: 'italic' }}>
                  En attente d'événements...
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

function StatBlock({ label, value, unit, sub, color, status, statusColor }) {
  return (
    <div className="stat-block">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <span className="stat-label">{label}</span>
        {status && (
          <span className={`status-badge ${statusColor}`} style={{ fontSize: 9 }}>{status}</span>
        )}
      </div>
      <div>
        <span className="stat-value" style={{ color }}>{value}</span>
        {unit && <span className="stat-unit">{unit}</span>}
      </div>
      {sub && <span style={{ fontSize: 11, color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>{sub}</span>}
    </div>
  );
}

function ServiceRow({ name, status, detail }) {
  return (
    <div className="status-row">
      <span className="status-name">{name}</span>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ fontSize: 10, fontFamily: 'var(--font-mono)', color: 'var(--text-muted)' }}>{detail}</span>
        <span className={`status-badge ${status ? 'ok' : 'err'}`}>
          {status ? 'UP' : 'DOWN'}
        </span>
      </div>
    </div>
  );
}

function getSourceColor(source) {
  switch (source) {
    case 'GAMA': return 'var(--accent)';
    case 'CARLA': return 'var(--green)';
    case 'ORCHESTRATOR': return 'var(--amber)';
    case 'SYSTEM': return 'var(--purple)';
    default: return 'var(--text-muted)';
  }
}

function getSourceBg(source) {
  switch (source) {
    case 'GAMA': return 'var(--accent-dim)';
    case 'CARLA': return 'var(--green-dim)';
    case 'ORCHESTRATOR': return 'var(--amber-dim)';
    case 'SYSTEM': return 'var(--purple-dim)';
    default: return 'rgba(255,255,255,0.04)';
  }
}
