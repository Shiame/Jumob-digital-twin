import React from 'react';

const RuleEnginePanel = ({ state }) => {
  const isActive = !!state.activeRule;
  
  return (
    <div className="glass-panel" style={{ paddingBottom: '2rem' }}>
      <div className="panel-title">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <polygon points="12 2 2 7 12 12 22 7 12 2"></polygon>
          <polyline points="2 17 12 22 22 17"></polyline>
          <polyline points="2 12 12 17 22 12"></polyline>
        </svg>
        Orchestrator Engine
      </div>

      <div style={{ marginTop: '1rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
          <span style={{ color: 'var(--text-muted)' }}>Status</span>
          <span style={{ color: isActive ? 'var(--status-warn)' : 'var(--status-ok)', fontWeight: 'bold' }}>
            {isActive ? 'RULE TRIGGERED' : 'IDLE'}
          </span>
        </div>
        
        {isActive && (
          <div style={{ 
            marginTop: '1rem', 
            padding: '1rem', 
            background: 'rgba(245, 158, 11, 0.1)', 
            borderLeft: '4px solid var(--status-warn)',
            borderRadius: '0 8px 8px 0'
          }}>
            <div style={{ fontSize: '0.8rem', color: 'var(--status-warn)', textTransform: 'uppercase', marginBottom: '0.25rem' }}>
              Active Rule
            </div>
            <div style={{ fontWeight: '600', marginBottom: '0.5rem' }}>{state.activeRule}</div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.9rem' }}>
              <span style={{ color: 'var(--text-muted)' }}>Target Speed:</span>
              <span style={{ fontWeight: 'bold' }}>{state.targetSpeed} km/h</span>
            </div>
          </div>
        )}
        
        {!isActive && (
          <div style={{
            marginTop: '1rem',
            padding: '1rem',
            background: 'rgba(16, 185, 129, 0.05)',
            borderLeft: '4px solid var(--status-ok)',
            borderRadius: '0 8px 8px 0',
            color: 'var(--text-muted)'
          }}>
            Monitoring conditions... No active violations detected.
          </div>
        )}
      </div>
    </div>
  );
};

export default RuleEnginePanel;
