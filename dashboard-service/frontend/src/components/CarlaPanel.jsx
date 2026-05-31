import React from 'react';

const CarlaPanel = ({ state, rules }) => {
  const isSlowed = rules.targetSpeed && state.speed <= rules.targetSpeed * 1.5;
  const statusColor = isSlowed ? 'var(--status-warn)' : 'var(--status-ok)';
  
  return (
    <div className="glass-panel" style={{ flex: 1 }}>
      <div className="panel-title">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M14 16H9m10 0h3v-3.15a1 1 0 0 0-.84-.99L16 11l-2.7-3.6a2 2 0 0 0-1.6-.8H9.3a2 2 0 0 0-1.6.8L5 11l-5.16.86a1 1 0 0 0-.84.99V16h3m10 0a3 3 0 1 1-6 0m6 0a3 3 0 1 0-6 0M9 16a3 3 0 1 1-6 0m6 0a3 3 0 1 0-6 0"></path>
        </svg>
        CARLA World (Micro)
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem', marginTop: '1rem', flex: 1, justifyContent: 'center' }}>
        
        {/* Speedometer */}
        <div style={{ textAlign: 'center' }}>
          <div style={{ position: 'relative', display: 'inline-block' }}>
            <svg width="200" height="120" viewBox="0 0 200 120">
              <path d="M 20 100 A 80 80 0 0 1 180 100" fill="none" stroke="rgba(255,255,255,0.1)" strokeWidth="15" strokeLinecap="round" />
              <path d="M 20 100 A 80 80 0 0 1 180 100" fill="none" stroke={statusColor} strokeWidth="15" strokeLinecap="round" 
                    strokeDasharray="251" strokeDashoffset={251 - (251 * Math.min(state.speed, 50) / 50)} 
                    style={{ transition: 'stroke-dashoffset 0.5s ease' }} />
            </svg>
            <div style={{ position: 'absolute', bottom: '10px', width: '100%', textAlign: 'center' }}>
              <div style={{ fontSize: '3rem', fontWeight: '800', lineHeight: '1' }}>{state.speed.toFixed(1)}</div>
              <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem', textTransform: 'uppercase' }}>km/h</div>
            </div>
          </div>
        </div>

        {/* Status indicator */}
        <div style={{ 
          background: isSlowed ? 'rgba(245, 158, 11, 0.1)' : 'rgba(16, 185, 129, 0.1)',
          border: `1px solid ${isSlowed ? 'rgba(245, 158, 11, 0.3)' : 'rgba(16, 185, 129, 0.3)'}`,
          borderRadius: '8px',
          padding: '1rem',
          textAlign: 'center',
          color: statusColor,
          fontWeight: '700',
          letterSpacing: '0.1em',
          textTransform: 'uppercase'
        }}>
          {isSlowed ? '⚠️ SLOWDOWN ACTIVE' : '🟢 NORMAL CRUISING'}
        </div>
        
        <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0 1rem' }}>
          <span style={{ color: 'var(--text-muted)' }}>Autopilot</span>
          <span style={{ color: state.autopilot ? 'var(--status-ok)' : 'var(--status-err)', fontWeight: '600' }}>
            {state.autopilot ? 'ENGAGED' : 'OVERRIDDEN'}
          </span>
        </div>
      </div>
    </div>
  );
};

export default CarlaPanel;
