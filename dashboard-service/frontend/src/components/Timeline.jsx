import React from 'react';

const Timeline = ({ events }) => {
  return (
    <div className="glass-panel" style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
      <div className="panel-title">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="12" cy="12" r="10"></circle>
          <polyline points="12 6 12 12 16 14"></polyline>
        </svg>
        Live Timeline
      </div>
      
      <div style={{ flex: 1, overflowY: 'auto', marginTop: '1rem', paddingRight: '0.5rem' }}>
        {events.length === 0 ? (
          <div style={{ color: 'var(--text-muted)', textAlign: 'center', marginTop: '2rem', fontStyle: 'italic' }}>
            Waiting for events...
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', position: 'relative' }}>
            {/* Vertical connecting line */}
            <div style={{ 
              position: 'absolute', 
              left: '11px', 
              top: '10px', 
              bottom: '10px', 
              width: '2px', 
              background: 'var(--panel-border)',
              zIndex: 0
            }}></div>
            
            {events.map((event, i) => (
              <TimelineEvent key={event.id || i} event={event} isFirst={i === 0} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

const TimelineEvent = ({ event, isFirst }) => {
  // Format time (HH:MM:SS)
  const timeStr = event.time instanceof Date 
    ? event.time.toLocaleTimeString([], { hour12: false }) 
    : new Date().toLocaleTimeString([], { hour12: false });
    
  let color = 'var(--accent-blue)';
  if (event.type === 'warning') color = 'var(--status-warn)';
  if (event.type === 'error') color = 'var(--status-err)';
  if (event.type === 'success') color = 'var(--status-ok)';
  
  if (event.source === 'GAMA') color = 'var(--accent-blue)';
  if (event.source === 'ORCHESTRATOR') color = 'var(--status-warn)';
  if (event.source === 'CARLA') color = 'var(--status-ok)';

  return (
    <div style={{ 
      display: 'flex', 
      gap: '1rem', 
      position: 'relative', 
      zIndex: 1,
      animation: isFirst ? 'slideIn 0.3s ease-out' : 'none'
    }}>
      {/* Node dot */}
      <div style={{ 
        width: '24px', 
        height: '24px', 
        borderRadius: '50%', 
        backgroundColor: 'var(--bg-color)',
        border: `3px solid ${color}`,
        flexShrink: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        boxShadow: isFirst ? `0 0 10px ${color}80` : 'none'
      }}>
        {isFirst && <div style={{ width: '6px', height: '6px', backgroundColor: color, borderRadius: '50%' }}></div>}
      </div>
      
      {/* Content */}
      <div style={{ 
        background: 'rgba(255,255,255,0.02)', 
        border: '1px solid rgba(255,255,255,0.05)',
        borderRadius: '8px',
        padding: '0.75rem 1rem',
        flex: 1,
        opacity: isFirst ? 1 : 0.7,
        transition: 'opacity 0.2s'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.25rem', fontSize: '0.8rem' }}>
          <span style={{ color, fontWeight: 'bold', letterSpacing: '0.05em' }}>{event.source}</span>
          <span style={{ color: 'var(--text-muted)' }}>{timeStr}</span>
        </div>
        <div style={{ color: 'var(--text-main)', fontSize: '0.95rem', lineHeight: '1.4' }}>
          {event.message}
        </div>
      </div>
    </div>
  );
};

export default Timeline;
