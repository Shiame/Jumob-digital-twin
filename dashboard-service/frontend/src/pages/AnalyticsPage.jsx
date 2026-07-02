import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  AreaChart, Area, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  ReferenceLine,
} from 'recharts';

const MAX_POINTS = 40;

const CustomTooltip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null;
  return (
    <div style={{
      background: '#313b50', border: '1px solid rgba(203,213,225,0.15)',
      borderRadius: 8, padding: '8px 14px', fontSize: 11,
      fontFamily: 'var(--font-mono)', boxShadow: '0 4px 16px rgba(0,0,0,0.3)',
    }}>
      <div style={{ color: '#6b7d94', marginBottom: 4 }}>{label}</div>
      {payload.map((p, i) => (
        <div key={i} style={{ color: p.color, display: 'flex', gap: 12, justifyContent: 'space-between' }}>
          <span>{p.name}</span>
          <span style={{ fontWeight: 600 }}>{typeof p.value === 'number' ? p.value.toFixed(1) : p.value}</span>
        </div>
      ))}
    </div>
  );
};

/** Show only HH:MM on axes, not seconds */
function shortTime() {
  const d = new Date();
  return d.toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

export default function AnalyticsPage({ gamaState, carlaState, egoVehicle, commands, throughput }) {
  const [speedHistory, setSpeedHistory] = useState([]);
  const [pedestrianHistory, setPedestrianHistory] = useState([]);
  const [congestionHistory, setCongestionHistory] = useState([]);
  const [throughputHistory, setThroughputHistory] = useState([]);
  const tickRef = useRef(0);

  const egoSpeed = egoVehicle?.speed_kmh ?? 0;
  const pedestrianCount = gamaState.pedestrians?.length || 0;
  const avgCongestion = useMemo(() => {
    const roads = gamaState.roads || [];
    if (!roads.length) return 0;
    return roads.reduce((s, r) => s + (r.congestionLevel || 0), 0) / roads.length;
  }, [gamaState.roads]);

  const activeSpeedLimit = useMemo(() => {
    const latest = commands.find(c => c.commandType === 'SET_SPEED_LIMIT');
    return latest?.maxSpeedKmh ?? null;
  }, [commands]);

  useEffect(() => {
    tickRef.current++;
    const timeLabel = shortTime();

    setSpeedHistory(prev => [
      ...prev.slice(-(MAX_POINTS - 1)),
      { time: timeLabel, speed: egoSpeed, limit: activeSpeedLimit },
    ]);

    setPedestrianHistory(prev => [
      ...prev.slice(-(MAX_POINTS - 1)),
      { time: timeLabel, count: pedestrianCount },
    ]);

    setCongestionHistory(prev => [
      ...prev.slice(-(MAX_POINTS - 1)),
      { time: timeLabel, congestion: +(avgCongestion * 100).toFixed(1) },
    ]);

    setThroughputHistory(prev => [
      ...prev.slice(-(MAX_POINTS - 1)),
      { time: timeLabel, gama: throughput.gama, carla: throughput.carla },
    ]);
  }, [gamaState.cycle, carlaState.tickNumber]);

  return (
    <>
      <div className="page-header">
        <span className="page-title">Analytique</span>
        <div className="page-meta">
          <div className="meta-item">
            <span>Points</span>
            <span className="meta-value">{speedHistory.length}/{MAX_POINTS}</span>
          </div>
          <div className="meta-item">
            <span>Débit</span>
            <span className="meta-value">{throughput.gama + throughput.carla} msg/s</span>
          </div>
        </div>
      </div>

      <div className="page-body" style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
        {/* Row 1: Speed + Pedestrians */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 22 }}>
          {/* Speed Chart */}
          <div className="card">
            <div className="card-header">
              <span className="card-title">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z" />
                  <path d="M12 6v6l4 2" />
                </svg>
                Vitesse véhicule ego
              </span>
              <span className="card-badge" style={{
                background: activeSpeedLimit ? 'var(--amber-dim)' : 'var(--green-dim)',
                color: activeSpeedLimit ? 'var(--amber)' : 'var(--green)',
              }}>
                {egoSpeed.toFixed(1)} km/h
              </span>
            </div>
            <div className="card-body">
              <div className="chart-container">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={speedHistory}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="time" tick={{ fontSize: 9 }} interval={Math.max(Math.floor(speedHistory.length / 5), 1)} />
                    <YAxis domain={[0, 60]} tick={{ fontSize: 9 }} width={35} />
                    <Tooltip content={<CustomTooltip />} />
                    <defs>
                      <linearGradient id="speedGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#34d399" stopOpacity={0.25} />
                        <stop offset="100%" stopColor="#34d399" stopOpacity={0.02} />
                      </linearGradient>
                    </defs>
                    <Area
                      type="monotone" dataKey="speed" name="Vitesse"
                      stroke="#34d399" fill="url(#speedGrad)" strokeWidth={2}
                      dot={false} isAnimationActive={false}
                    />
                    {activeSpeedLimit && (
                      <ReferenceLine
                        y={activeSpeedLimit}
                        stroke="#fbbf24"
                        strokeDasharray="6 3"
                        strokeWidth={1.5}
                        label={{ value: `Limite ${activeSpeedLimit}`, position: 'right', fill: '#fbbf24', fontSize: 10 }}
                      />
                    )}
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          {/* Pedestrian Chart */}
          <div className="card">
            <div className="card-header">
              <span className="card-title">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                  <circle cx="9" cy="7" r="4" />
                  <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                  <path d="M16 3.13a4 4 0 0 1 0 7.75" />
                </svg>
                Densité piétonne
              </span>
              <span className="card-badge" style={{ background: 'var(--accent-dim)', color: 'var(--accent)' }}>
                {pedestrianCount} agents
              </span>
            </div>
            <div className="card-body">
              <div className="chart-container">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={pedestrianHistory}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="time" tick={{ fontSize: 9 }} interval={Math.max(Math.floor(pedestrianHistory.length / 5), 1)} />
                    <YAxis tick={{ fontSize: 9 }} width={35} />
                    <Tooltip content={<CustomTooltip />} />
                    <defs>
                      <linearGradient id="pedGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#60a5fa" stopOpacity={0.25} />
                        <stop offset="100%" stopColor="#60a5fa" stopOpacity={0.02} />
                      </linearGradient>
                    </defs>
                    <Area
                      type="monotone" dataKey="count" name="Piétons"
                      stroke="#60a5fa" fill="url(#pedGrad)" strokeWidth={2}
                      dot={false} isAnimationActive={false}
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>
        </div>

        {/* Row 2: Congestion + Throughput */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 22 }}>
          {/* Congestion */}
          <div className="card">
            <div className="card-header">
              <span className="card-title">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
                </svg>
                Congestion réseau routier
              </span>
              <span className="card-badge" style={{
                background: avgCongestion > 0.4 ? 'var(--red-dim)' : 'var(--green-dim)',
                color: avgCongestion > 0.4 ? 'var(--red)' : 'var(--green)',
              }}>
                {(avgCongestion * 100).toFixed(0)}%
              </span>
            </div>
            <div className="card-body">
              <div className="chart-container">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={congestionHistory}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="time" tick={{ fontSize: 9 }} interval={Math.max(Math.floor(congestionHistory.length / 5), 1)} />
                    <YAxis domain={[0, 100]} tick={{ fontSize: 9 }} unit="%" width={40} />
                    <Tooltip content={<CustomTooltip />} />
                    <defs>
                      <linearGradient id="congGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#fbbf24" stopOpacity={0.25} />
                        <stop offset="100%" stopColor="#fbbf24" stopOpacity={0.02} />
                      </linearGradient>
                    </defs>
                    <Area
                      type="monotone" dataKey="congestion" name="Congestion"
                      stroke="#fbbf24" fill="url(#congGrad)" strokeWidth={2}
                      dot={false} isAnimationActive={false}
                    />
                    <ReferenceLine y={50} stroke="#f87171" strokeDasharray="6 3" strokeWidth={1} />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          {/* Throughput */}
          <div className="card">
            <div className="card-header">
              <span className="card-title">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <polyline points="23 6 13.5 15.5 8.5 10.5 1 18" />
                  <polyline points="17 6 23 6 23 12" />
                </svg>
                Débit Kafka
              </span>
              <span className="card-badge" style={{ background: 'var(--cyan-dim)', color: 'var(--cyan)' }}>
                {throughput.gama + throughput.carla} msg/s
              </span>
            </div>
            <div className="card-body">
              <div className="chart-container">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={throughputHistory} barGap={1}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="time" tick={{ fontSize: 9 }} interval={Math.max(Math.floor(throughputHistory.length / 5), 1)} />
                    <YAxis tick={{ fontSize: 9 }} width={30} />
                    <Tooltip content={<CustomTooltip />} />
                    <Bar dataKey="gama" name="GAMA" fill="#60a5fa" opacity={0.75} radius={[3, 3, 0, 0]} maxBarSize={12} />
                    <Bar dataKey="carla" name="CARLA" fill="#34d399" opacity={0.75} radius={[3, 3, 0, 0]} maxBarSize={12} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>
        </div>

        {/* Row 3: Road details */}
        <div className="card">
          <div className="card-header">
            <span className="card-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" />
                <line x1="4" y1="22" x2="4" y2="15" />
              </svg>
              Segments routiers GAMA
            </span>
          </div>
          <div className="card-body" style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12, fontFamily: 'var(--font-mono)' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border-default)' }}>
                  <th style={thStyle}>Road ID</th>
                  <th style={thStyle}>Congestion</th>
                  <th style={thStyle}>Speed Coeff</th>
                  <th style={thStyle}>Bloquée</th>
                  <th style={thStyle}>Agents</th>
                </tr>
              </thead>
              <tbody>
                {(gamaState.roads || []).map(road => (
                  <tr key={road.roadId} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                    <td style={tdStyle}>{road.roadId}</td>
                    <td style={tdStyle}>
                      <CongestionBar value={road.congestionLevel || 0} />
                    </td>
                    <td style={tdStyle}>{(road.speedCoeff || 0).toFixed(2)}</td>
                    <td style={tdStyle}>
                      <span className={`status-badge ${road.blocked ? 'err' : 'idle'}`}>
                        {road.blocked ? 'OUI' : 'NON'}
                      </span>
                    </td>
                    <td style={tdStyle}>{Object.keys(road.movingAgents || {}).length}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </>
  );
}

function CongestionBar({ value }) {
  const pct = Math.min(value * 100, 100);
  const color = pct > 60 ? 'var(--red)' : pct > 30 ? 'var(--amber)' : 'var(--green)';
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <div style={{
        width: 60, height: 6, background: 'rgba(203,213,225,0.06)',
        borderRadius: 3, overflow: 'hidden',
      }}>
        <div style={{
          width: `${pct}%`, height: '100%', background: color,
          borderRadius: 3, transition: 'width 0.3s ease',
        }} />
      </div>
      <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>{pct.toFixed(0)}%</span>
    </div>
  );
}

const thStyle = {
  textAlign: 'left', padding: '10px 12px',
  fontSize: 10, fontWeight: 600, color: 'var(--text-muted)',
  textTransform: 'uppercase', letterSpacing: '0.05em',
};

const tdStyle = {
  padding: '10px 12px', color: 'var(--text-secondary)',
};
