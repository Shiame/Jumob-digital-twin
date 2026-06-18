import React, { useState, useMemo } from 'react';

export default function EventsPage({ events, commands }) {
  const [filter, setFilter] = useState('ALL');

  const filteredEvents = useMemo(() => {
    if (filter === 'ALL') return events;
    return events.filter(e => e.source === filter);
  }, [events, filter]);

  const counts = useMemo(() => ({
    ALL: events.length,
    SYSTEM: events.filter(e => e.source === 'SYSTEM').length,
    GAMA: events.filter(e => e.source === 'GAMA').length,
    CARLA: events.filter(e => e.source === 'CARLA').length,
    ORCHESTRATOR: events.filter(e => e.source === 'ORCHESTRATOR').length,
  }), [events]);

  return (
    <>
      <div className="page-header">
        <span className="page-title">Journal d'événements</span>
        <div className="page-meta">
          <div className="meta-item">
            <span>Total</span>
            <span className="meta-value">{events.length}</span>
          </div>
          <div className="meta-item">
            <span>Commandes</span>
            <span className="meta-value">{commands.length}</span>
          </div>
        </div>
      </div>

      <div className="page-body" style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        {/* Filter bar */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div className="filter-bar">
            {['ALL', 'SYSTEM', 'GAMA', 'CARLA', 'ORCHESTRATOR'].map(f => (
              <button
                key={f}
                className={`filter-btn ${filter === f ? 'active' : ''}`}
                onClick={() => setFilter(f)}
              >
                {f === 'ALL' ? 'Tout' : f} {counts[f] > 0 && <span style={{ opacity: 0.6, marginLeft: 3 }}>({counts[f]})</span>}
              </button>
            ))}
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 380px', gap: 20 }}>
          {/* Event stream */}
          <div className="card">
            <div className="card-header">
              <span className="card-title">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="10" />
                  <polyline points="12 6 12 12 16 14" />
                </svg>
                Flux d'événements
              </span>
              <span className="card-badge" style={{ background: 'var(--accent-dim)', color: 'var(--accent)' }}>
                {filteredEvents.length}
              </span>
            </div>
            <div className="card-body" style={{ maxHeight: 'calc(100vh - 240px)', overflowY: 'auto' }}>
              {filteredEvents.length === 0 ? (
                <div style={{ color: 'var(--text-muted)', textAlign: 'center', padding: 40, fontStyle: 'italic' }}>
                  Aucun événement {filter !== 'ALL' ? `pour ${filter}` : ''}
                </div>
              ) : (
                filteredEvents.map((ev, i) => (
                  <div key={ev.id} className="event-item" style={{
                    animation: i === 0 ? 'slide-up 0.2s ease-out' : 'none',
                  }}>
                    <div className="event-dot-col">
                      <div className="event-dot" style={{ background: getSourceColor(ev.source) }} />
                      {i < filteredEvents.length - 1 && <div className="event-line" />}
                    </div>
                    <div className="event-content">
                      <div className="event-header">
                        <span className="event-source" style={{
                          background: getSourceBg(ev.source),
                          color: getSourceColor(ev.source),
                        }}>{ev.source}</span>
                        <span className="event-time">
                          {ev.time instanceof Date ? ev.time.toLocaleTimeString([], { hour12: false }) : ''}
                        </span>
                        {ev.type === 'error' && (
                          <span style={{ fontSize: 9, fontWeight: 700, color: 'var(--red)', fontFamily: 'var(--font-mono)', marginLeft: 'auto' }}>CRITICAL</span>
                        )}
                        {ev.type === 'warning' && (
                          <span style={{ fontSize: 9, fontWeight: 700, color: 'var(--amber)', fontFamily: 'var(--font-mono)', marginLeft: 'auto' }}>WARNING</span>
                        )}
                      </div>
                      <div className="event-message">{ev.message}</div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Commands history */}
          <div className="card">
            <div className="card-header">
              <span className="card-title">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <polyline points="16 3 21 3 21 8" />
                  <line x1="4" y1="20" x2="21" y2="3" />
                  <polyline points="21 16 21 21 16 21" />
                  <line x1="15" y1="15" x2="21" y2="21" />
                  <line x1="4" y1="4" x2="9" y2="9" />
                </svg>
                Commandes orchestrateur
              </span>
              <span className="card-badge" style={{ background: 'var(--amber-dim)', color: 'var(--amber)' }}>
                {commands.length}
              </span>
            </div>
            <div className="card-body" style={{ maxHeight: 'calc(100vh - 240px)', overflowY: 'auto' }}>
              {commands.length === 0 ? (
                <div style={{ color: 'var(--text-muted)', textAlign: 'center', padding: 40, fontStyle: 'italic' }}>
                  Aucune commande reçue
                </div>
              ) : (
                commands.slice(0, 30).map((cmd, i) => (
                  <div key={cmd.commandId || i} style={{
                    padding: '10px 0',
                    borderBottom: '1px solid var(--border-subtle)',
                    animation: i === 0 ? 'slide-up 0.2s ease-out' : 'none',
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                      <span style={{
                        fontSize: 11, fontWeight: 700, fontFamily: 'var(--font-mono)',
                        color: cmd.commandType === 'SET_SPEED_LIMIT' ? 'var(--amber)' : 'var(--red)',
                      }}>
                        {cmd.commandType}
                      </span>
                      <span style={{ fontSize: 10, fontFamily: 'var(--font-mono)', color: 'var(--text-muted)' }}>
                        {cmd.receivedAt instanceof Date ? cmd.receivedAt.toLocaleTimeString([], { hour12: false }) : ''}
                      </span>
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 2, fontSize: 11, color: 'var(--text-secondary)' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                        <span style={{ color: 'var(--text-muted)' }}>Règle</span>
                        <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 500 }}>{cmd.triggeredBy}</span>
                      </div>
                      {cmd.maxSpeedKmh && (
                        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                          <span style={{ color: 'var(--text-muted)' }}>Limite</span>
                          <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>{cmd.maxSpeedKmh} km/h</span>
                        </div>
                      )}
                      {cmd.reason && (
                        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                          <span style={{ color: 'var(--text-muted)' }}>Raison</span>
                          <span style={{ fontFamily: 'var(--font-mono)', fontSize: 10 }}>{cmd.reason}</span>
                        </div>
                      )}
                      {cmd.pedestrianCount != null && (
                        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                          <span style={{ color: 'var(--text-muted)' }}>Piétons</span>
                          <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 500 }}>{cmd.pedestrianCount}</span>
                        </div>
                      )}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </>
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
