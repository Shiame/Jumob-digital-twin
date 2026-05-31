import React from 'react';

const StatusPanel = ({ connected }) => {
  const statusColor = connected ? 'var(--status-ok)' : 'var(--status-err)';
  const statusGlow = connected ? 'var(--glow-ok)' : 'var(--glow-err)';

  return (
    <div className="glass-panel">
      <div className="panel-title">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M5 12h14M12 5l7 7-7 7"/>
        </svg>
        Services Status
      </div>
      
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '0.5rem' }}>
        <StatusRow label="Mission Control Backend" active={connected} />
        <StatusRow label="Kafka Broker" active={connected} />
        <StatusRow label="GAMA Visualizer" active={connected} />
        <StatusRow label="CARLA Adapter" active={connected} />
        <StatusRow label="Orchestrator Rule Engine" active={connected} />
      </div>
    </div>
  );
};

const StatusRow = ({ label, active }) => (
  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
    <span style={{ color: active ? 'var(--text-main)' : 'var(--text-muted)' }}>{label}</span>
    <div style={{
      width: '12px',
      height: '12px',
      borderRadius: '50%',
      backgroundColor: active ? 'var(--status-ok)' : 'var(--status-err)',
      boxShadow: `0 0 8px ${active ? 'var(--glow-ok)' : 'var(--glow-err)'}`
    }}></div>
  </div>
);

export default StatusPanel;
