import React, { useEffect, useRef } from 'react';

/**
 * Animated Canvas diagram showing the CORRECT system architecture:
 *
 *   GAMA Adapter ──→ KAFKA ←── CARLA Adapter
 *                      │
 *                      ▼
 *                 ORCHESTRATOR
 *                      │
 *                      ▼
 *                    KAFKA (commands back)
 *
 * Kafka is the central hub — both adapters publish to it,
 * and the orchestrator consumes from it and publishes commands back.
 */
export default function TopologyDiagram({ throughput, connected, isDarkMode }) {
  const canvasRef = useRef(null);
  const particlesRef = useRef([]);
  const animRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    const dpr = window.devicePixelRatio || 1;

    const resize = () => {
      const rect = canvas.getBoundingClientRect();
      canvas.width = rect.width * dpr;
      canvas.height = rect.height * dpr;
      ctx.scale(dpr, dpr);
    };
    resize();

    /*
     * Layout (hub-and-spoke with Kafka at center):
     *
     *  GAMA ─────→  KAFKA  ←───── CARLA
     *                 │  ↑
     *                 ↓  │ (commands)
     *             ORCHESTRATOR
     */
    const getNodes = () => {
      const w = canvas.getBoundingClientRect().width;
      const h = canvas.getBoundingClientRect().height;
      const cx = w / 2;
      const topY = h * 0.32;
      const botY = h * 0.78;
      const sideSpread = Math.min(w * 0.38, 180);

      return {
        gama:         { x: cx - sideSpread, y: topY, label: 'GAMA ADAPTER',  sub: 'Macro-sim',   color: '#60a5fa', icon: 'G' },
        kafka:        { x: cx,              y: topY, label: 'KAFKA',          sub: 'Event Bus',   color: '#a78bfa', icon: 'K' },
        carla:        { x: cx + sideSpread, y: topY, label: 'CARLA ADAPTER', sub: 'Micro-sim',   color: '#34d399', icon: 'C' },
        orchestrator: { x: cx,              y: botY, label: 'ORCHESTRATOR',  sub: 'Rule Engine', color: '#fbbf24', icon: 'O' },
      };
    };

    const spawnParticle = (from, to, color) => {
      particlesRef.current.push({
        x: from.x, y: from.y,
        tx: to.x, ty: to.y,
        progress: 0,
        speed: 0.010 + Math.random() * 0.006,
        color,
        size: 2 + Math.random() * 1.5,
      });
    };

    let spawnTimer = 0;

    const drawConnection = (a, b, active) => {
      ctx.beginPath();
      ctx.moveTo(a.x, a.y);
      ctx.lineTo(b.x, b.y);
      ctx.strokeStyle = active ? (isDarkMode ? 'rgba(255,255,255,0.15)' : 'rgba(15, 23, 42, 0.15)') : (isDarkMode ? 'rgba(255,255,255,0.05)' : 'rgba(15, 23, 42, 0.05)');
      ctx.lineWidth = 1.5;
      ctx.setLineDash([4, 4]);
      ctx.stroke();
      ctx.setLineDash([]);
    };

    const drawArrow = (from, to, offset) => {
      const angle = Math.atan2(to.y - from.y, to.x - from.x);
      const tipX = to.x - Math.cos(angle) * offset;
      const tipY = to.y - Math.sin(angle) * offset;
      const size = 5;
      ctx.beginPath();
      ctx.moveTo(tipX, tipY);
      ctx.lineTo(tipX - size * Math.cos(angle - 0.4), tipY - size * Math.sin(angle - 0.4));
      ctx.lineTo(tipX - size * Math.cos(angle + 0.4), tipY - size * Math.sin(angle + 0.4));
      ctx.closePath();
      ctx.fillStyle = connected ? (isDarkMode ? 'rgba(255,255,255,0.3)' : 'rgba(15, 23, 42, 0.3)') : (isDarkMode ? 'rgba(255,255,255,0.1)' : 'rgba(15, 23, 42, 0.1)');
      ctx.fill();
    };

    const draw = () => {
      const rect = canvas.getBoundingClientRect();
      const w = rect.width;
      const h = rect.height;
      ctx.clearRect(0, 0, w, h);

      const n = getNodes();
      const nodeRadius = 22;

      // Draw connections
      drawConnection(n.gama, n.kafka, connected);   // GAMA → Kafka
      drawConnection(n.carla, n.kafka, connected);   // CARLA → Kafka
      drawConnection(n.kafka, n.orchestrator, connected); // Kafka → Orchestrator
      drawConnection(n.orchestrator, n.kafka, connected); // Orchestrator → Kafka (commands back)

      // Arrows
      drawArrow(n.gama, n.kafka, nodeRadius);
      drawArrow(n.carla, n.kafka, nodeRadius);
      drawArrow(n.kafka, n.orchestrator, nodeRadius);
      drawArrow(n.orchestrator, n.kafka, nodeRadius + 6); // offset slightly more for return path

      // Spawn particles
      if (connected) {
        spawnTimer++;
        if (spawnTimer % 10 === 0) {
          // GAMA Adapter → Kafka
          spawnParticle(n.gama, n.kafka, n.gama.color);
          // CARLA Adapter → Kafka
          spawnParticle(n.carla, n.kafka, n.carla.color);
        }
        if (spawnTimer % 14 === 0) {
          // Kafka → Orchestrator
          spawnParticle(n.kafka, n.orchestrator, n.kafka.color);
        }
        if (spawnTimer % 22 === 0) {
          // Orchestrator → Kafka (commands)
          spawnParticle(n.orchestrator, n.kafka, n.orchestrator.color);
        }
      }

      // Update and draw particles
      particlesRef.current = particlesRef.current.filter(p => {
        p.progress += p.speed;
        if (p.progress >= 1) return false;

        const x = p.x + (p.tx - p.x) * p.progress;
        const y = p.y + (p.ty - p.y) * p.progress;

        ctx.beginPath();
        ctx.arc(x, y, p.size, 0, Math.PI * 2);
        ctx.fillStyle = p.color;
        ctx.globalAlpha = 0.6 * Math.sin(p.progress * Math.PI);
        ctx.fill();
        ctx.globalAlpha = 1;

        return true;
      });

      // Draw node circles
      const allNodes = [n.gama, n.kafka, n.carla, n.orchestrator];
      allNodes.forEach(node => {
        ctx.save();
        
        // Circle bg & shadow
        ctx.beginPath();
        ctx.arc(node.x, node.y, nodeRadius, 0, Math.PI * 2);
        
        if (connected) {
          ctx.fillStyle = isDarkMode ? '#1e293b' : '#ffffff';
          ctx.shadowColor = node.color + '60';
          ctx.shadowBlur = 16;
          ctx.shadowOffsetY = 6;
          ctx.fill();
          
          ctx.shadowColor = 'transparent';
          ctx.strokeStyle = node.color;
          ctx.lineWidth = 3;
          ctx.stroke();
        } else {
          ctx.fillStyle = isDarkMode ? '#0f172a' : '#f8fafc';
          ctx.fill();
          ctx.strokeStyle = isDarkMode ? 'rgba(255,255,255,0.15)' : 'rgba(15,23,42,0.15)';
          ctx.lineWidth = 1.5;
          ctx.stroke();
        }
        ctx.restore();

        // Icon letter
        ctx.fillStyle = connected ? node.color : (isDarkMode ? 'rgba(255,255,255,0.3)' : 'rgba(15,23,42,0.3)');
        ctx.font = '800 16px Inter, sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(node.icon, node.x, node.y + 1);

        // Status dot
        ctx.beginPath();
        ctx.arc(node.x + 16, node.y - 16, 5, 0, Math.PI * 2);
        ctx.fillStyle = connected ? '#10b981' : '#f87171';
        ctx.strokeStyle = isDarkMode ? '#1e293b' : '#ffffff';
        ctx.lineWidth = 2;
        ctx.fill();
        ctx.stroke();

        // Label
        ctx.fillStyle = isDarkMode ? '#e2e8f0' : '#475569';
        ctx.font = '800 10px Inter, sans-serif';
        ctx.fillText(node.label, node.x, node.y + 36);

        // Sublabel
        ctx.fillStyle = isDarkMode ? '#94a3b8' : '#94a3b8';
        ctx.font = '500 9px Inter, sans-serif';
        ctx.fillText(node.sub, node.x, node.y + 48);
      });

      animRef.current = requestAnimationFrame(draw);
    };

    draw();

    const observer = new ResizeObserver(resize);
    observer.observe(canvas);

    return () => {
      cancelAnimationFrame(animRef.current);
      observer.disconnect();
    };
  }, [connected, throughput]);

  return (
    <canvas
      ref={canvasRef}
      style={{ width: '100%', height: 260, display: 'block' }}
    />
  );
}
