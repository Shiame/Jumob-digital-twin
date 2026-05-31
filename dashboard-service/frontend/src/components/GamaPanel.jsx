import React from 'react';

const GamaPanel = ({ state }) => {
  return (
    <div className="glass-panel" style={{ flex: 1 }}>
      <div className="panel-title">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
          <circle cx="9" cy="7" r="4"></circle>
          <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
          <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
        </svg>
        GAMA World (Macro)
      </div>
      
      <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem', marginTop: '1rem', flex: 1, justifyContent: 'center' }}>
        <MetricCard label="Piétons Actifs" value={state.pedestrians} color="var(--accent-blue)" />
        <MetricCard label="Véhicules" value={state.vehicles} color="#8b5cf6" />
      </div>
    </div>
  );
};

const MetricCard = ({ label, value, color }) => (
  <div style={{
    background: 'rgba(255, 255, 255, 0.03)',
    borderRadius: '12px',
    padding: '1.5rem',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '0.5rem',
    border: '1px solid rgba(255,255,255,0.05)'
  }}>
    <span style={{ fontSize: '3.5rem', fontWeight: '800', color: color, lineHeight: '1' }}>
      {value}
    </span>
    <span style={{ color: 'var(--text-muted)', fontSize: '1rem', textTransform: 'uppercase', letterSpacing: '0.1em' }}>
      {label}
    </span>
  </div>
);

export default GamaPanel;
