import React, { useMemo, useEffect, useState } from 'react';
import { MapContainer, TileLayer, CircleMarker, Circle, Popup, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';

// Default fallback center (Toulouse)
const DEFAULT_CENTER = [43.5529, 1.4595];

export default function MapPage({ gamaState, carlaState, egoVehicle, lastCommand }) {
  const pedestrians = useMemo(
    () => (gamaState.agents || []).filter(a => a.type === 'pedestrian'),
    [gamaState.agents]
  );

  const gamaVehicles = useMemo(
    () => (gamaState.agents || []).filter(a => a.type !== 'pedestrian'),
    [gamaState.agents]
  );

  // Calculate dynamic center based on GAMA agents
  const dynamicCenter = useMemo(() => {
    if (gamaState.agents && gamaState.agents.length > 0) {
      let sumLat = 0, sumLon = 0;
      gamaState.agents.forEach(a => {
        sumLat += a.y;
        sumLon += a.x;
      });
      return [sumLat / gamaState.agents.length, sumLon / gamaState.agents.length];
    }
    return null;
  }, [gamaState.agents]);

  const egoLat = egoVehicle?.latitude;
  const egoLon = egoVehicle?.longitude;
  const egoSpeed = egoVehicle?.speed_kmh ?? 0;

  const isSlowdown = lastCommand?.commandType === 'SET_SPEED_LIMIT';
  const proximityRadius = lastCommand?.radiusMeters || 50;

  return (
    <>
      <div className="page-header">
        <span className="page-title">Carte temps réel</span>
        <div className="page-meta">
          <div className="meta-item">
            <span>Piétons</span>
            <span className="meta-value" style={{ color: 'var(--accent)' }}>{pedestrians.length}</span>
          </div>
          <div className="meta-item">
            <span>Véhicules GAMA</span>
            <span className="meta-value" style={{ color: 'var(--purple)' }}>{gamaVehicles.length}</span>
          </div>
          <div className="meta-item">
            <span>Ego</span>
            <span className="meta-value" style={{ color: isSlowdown ? 'var(--amber)' : 'var(--green)' }}>
              {egoSpeed.toFixed(1)} km/h
            </span>
          </div>
        </div>
      </div>

      <div className="page-body" style={{ padding: 0, display: 'flex', flexDirection: 'column', height: 'calc(100vh - var(--header-height))' }}>
        <div style={{ flex: 1, position: 'relative' }}>
          <MapContainer
            center={DEFAULT_CENTER}
            zoom={16}
            style={{ width: '100%', height: '100%' }}
            zoomControl={true}
          >
            <TileLayer
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              attribution=""
            />

            <MapAutoCenter 
              egoLat={egoLat} 
              egoLon={egoLon} 
              dynamicCenter={dynamicCenter} 
            />

            {/* Proximity radius around ego */}
            {isSlowdown && egoLat && egoLon && (
              <Circle
                center={[egoLat, egoLon]}
                radius={proximityRadius}
                pathOptions={{
                  color: '#f59e0b',
                  fillColor: '#f59e0b',
                  fillOpacity: 0.08,
                  weight: 1,
                  dashArray: '6 4',
                }}
              />
            )}

            {/* Pedestrians */}
            {pedestrians.map(p => (
              <CircleMarker
                key={p.id}
                center={[p.y, p.x]}
                radius={3}
                pathOptions={{
                  color: '#2d7ff9',
                  fillColor: '#2d7ff9',
                  fillOpacity: 0.7,
                  weight: 0,
                }}
              >
                <Popup>
                  <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11 }}>
                    <strong>{p.id}</strong><br />
                    Vitesse: {p.speed?.toFixed(1)} m/s<br />
                    Route: {p.roadId}<br />
                    État: {p.state}
                  </div>
                </Popup>
              </CircleMarker>
            ))}

            {/* GAMA Vehicles */}
            {gamaVehicles.map(v => (
              <CircleMarker
                key={v.id}
                center={[v.y, v.x]}
                radius={4}
                pathOptions={{
                  color: typeColor(v.type),
                  fillColor: typeColor(v.type),
                  fillOpacity: 0.8,
                  weight: 1,
                }}
              >
                <Popup>
                  <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11 }}>
                    <strong>{v.id}</strong> ({v.type})<br />
                    Vitesse: {v.speed?.toFixed(1)}<br />
                    Route: {v.roadId}
                  </div>
                </Popup>
              </CircleMarker>
            ))}

            {/* Ego Vehicle (hero) */}
            {egoVehicle && egoLat && egoLon && (
              <CircleMarker
                center={[egoLat, egoLon]}
                radius={8}
                pathOptions={{
                  color: isSlowdown ? '#f59e0b' : '#22c55e',
                  fillColor: isSlowdown ? '#f59e0b' : '#22c55e',
                  fillOpacity: 0.9,
                  weight: 3,
                }}
              >
                <Popup>
                  <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11 }}>
                    <strong>EGO — {egoVehicle.type}</strong><br />
                    Vitesse: {egoSpeed.toFixed(1)} km/h<br />
                    GPS: {egoLat.toFixed(6)}, {egoLon.toFixed(6)}<br />
                    {isSlowdown && (
                      <span style={{ color: '#f59e0b' }}>
                        ⚠ Limite: {lastCommand.maxSpeedKmh} km/h
                      </span>
                    )}
                  </div>
                </Popup>
              </CircleMarker>
            )}
          </MapContainer>

          {/* Legend overlay */}
          <div className="map-legend">
            <div className="legend-item">
              <span className="legend-dot" style={{ background: '#2d7ff9' }} />
              <span>Piétons</span>
            </div>
            <div className="legend-item">
              <span className="legend-dot" style={{ background: '#8b5cf6' }} />
              <span>Voitures</span>
            </div>
            <div className="legend-item">
              <span className="legend-dot" style={{ background: '#f97316' }} />
              <span>Bus</span>
            </div>
            <div className="legend-item">
              <span className="legend-dot" style={{ background: '#06b6d4' }} />
              <span>Vélos</span>
            </div>
            <div className="legend-item">
              <span className="legend-dot" style={{ background: '#22c55e', width: 10, height: 10 }} />
              <span>Ego Vehicle</span>
            </div>
          </div>

          {/* Ego info overlay */}
          {egoVehicle && egoLat && egoLon && (
            <div style={{
              position: 'absolute', top: 16, right: 16, zIndex: 1000,
              background: 'var(--bg-elevated)', border: '1px solid var(--border-default)',
              borderRadius: 'var(--radius-md)', padding: '12px 16px',
              minWidth: 180,
            }}>
              <div style={{ fontSize: 10, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 8 }}>
                Véhicule Autonome
              </div>
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: 28, fontWeight: 700, color: isSlowdown ? 'var(--amber)' : 'var(--green)', lineHeight: 1 }}>
                {egoSpeed.toFixed(1)}
              </div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 8 }}>km/h</div>
              <div style={{ fontSize: 11, fontFamily: 'var(--font-mono)', color: 'var(--text-secondary)' }}>
                {egoLat.toFixed(5)}, {egoLon.toFixed(5)}
              </div>
              {isSlowdown && (
                <div style={{
                  marginTop: 8, padding: '4px 8px', borderRadius: 4,
                  background: 'var(--amber-dim)', color: 'var(--amber)',
                  fontSize: 10, fontWeight: 600, fontFamily: 'var(--font-mono)',
                  textAlign: 'center',
                }}>
                  LIMITE {lastCommand.maxSpeedKmh} KM/H
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </>
  );
}

/** Auto-center map on ego vehicle or agent cluster (soft pan) */
function MapAutoCenter({ egoLat, egoLon, dynamicCenter }) {
  const map = useMap();
  const [hasCentered, setHasCentered] = useState(false);

  useEffect(() => {
    // 1. If ego vehicle exists, always track it
    if (egoLat && egoLon) {
      map.setView([egoLat, egoLon], map.getZoom(), { animate: true, duration: 0.5 });
      setHasCentered(true);
    } 
    // 2. Otherwise, center on the GAMA agents once when they load
    else if (!hasCentered && dynamicCenter) {
      map.setView(dynamicCenter, 16, { animate: true, duration: 1 });
      setHasCentered(true);
    }
  }, [egoLat, egoLon, dynamicCenter, map, hasCentered]);
  
  return null;
}

function typeColor(type) {
  switch (type) {
    case 'car': return '#8b5cf6';
    case 'bus': return '#f97316';
    case 'bike': return '#06b6d4';
    default: return '#8b5cf6';
  }
}
