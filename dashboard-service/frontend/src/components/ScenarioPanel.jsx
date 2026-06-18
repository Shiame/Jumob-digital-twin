import React, { useMemo } from 'react';

const SCENARIOS = [
  {
    id: 'S1',
    name: 'Densité piétonne → Réduction vitesse',
    description: 'Quand GAMA détecte une densité élevée de piétons à proximité du véhicule ego, l\'orchestrateur réduit la limite de vitesse dans CARLA.',
    commandType: 'SET_SPEED_LIMIT',
    triggerPattern: 'PEDESTRIAN_DENSITY',
    color: 'var(--amber)',
    bg: 'var(--amber-dim)',
  },
  {
    id: 'S2',
    name: 'Collision → Blocage route',
    description: 'Quand CARLA détecte une collision, l\'orchestrateur envoie un ordre de blocage de route à GAMA pour rerouter le trafic.',
    commandType: 'BLOCK_ROAD',
    triggerPattern: 'COLLISION',
    color: 'var(--red)',
    bg: 'var(--red-dim)',
  },
];

export default function ScenarioPanel({ commands, lastCommand, events }) {
  const scenarioStats = useMemo(() => {
    return SCENARIOS.map(s => {
      const relatedCommands = commands.filter(c => c.commandType === s.commandType);
      const lastTrigger = relatedCommands[0];
      const isActive = lastCommand?.commandType === s.commandType;

      return {
        ...s,
        triggerCount: relatedCommands.length,
        lastTrigger,
        isActive,
      };
    });
  }, [commands, lastCommand]);

  return (
    <>
      {scenarioStats.map(s => (
        <div
          key={s.id}
          className={`scenario-item ${s.isActive ? 'triggered' : ''}`}
        >
          <div className="scenario-head">
            <div className="scenario-name">
              <div className="scenario-icon" style={{ background: s.bg, color: s.color, fontSize: 12, fontWeight: 700, fontFamily: 'var(--font-mono)' }}>
                {s.id}
              </div>
              <span>{s.name}</span>
            </div>
            <span className={`status-badge ${s.isActive ? 'warn' : 'idle'}`}>
              {s.isActive ? 'ACTIF' : 'VEILLE'}
            </span>
          </div>

          <div style={{ fontSize: 11, color: 'var(--text-muted)', lineHeight: 1.5 }}>
            {s.description}
          </div>

          <div className="scenario-stats">
            <span>Déclenchements: <span className="s-val">{s.triggerCount}</span></span>
            {s.lastTrigger && (
              <span>Dernier: <span className="s-val">
                {s.lastTrigger.receivedAt instanceof Date
                  ? s.lastTrigger.receivedAt.toLocaleTimeString([], { hour12: false })
                  : '—'}
              </span></span>
            )}
            {s.isActive && s.lastTrigger?.maxSpeedKmh && (
              <span style={{ color: 'var(--amber)' }}>
                Limite: <span className="s-val">{s.lastTrigger.maxSpeedKmh} km/h</span>
              </span>
            )}
          </div>
        </div>
      ))}
    </>
  );
}
