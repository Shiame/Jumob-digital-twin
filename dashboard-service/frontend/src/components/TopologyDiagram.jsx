import React, { useEffect, useRef } from 'react';

/**
 * Animated SVG diagram showing the system architecture:
 * GAMA → Kafka → Orchestrateur → CARLA
 * with animated data flow particles on each connection.
 */
export default function TopologyDiagram({ throughput, connected }) {
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

    // Node positions (relative to canvas size)
    const getNodes = () => {
      const w = canvas.getBoundingClientRect().width;
      const h = canvas.getBoundingClientRect().height;
      const cy = h / 2;
      const pad = 50;
      const spacing = (w - pad * 2) / 3;
      return [
        { x: pad, y: cy, label: 'GAMA', sub: 'Macro-sim', color: '#2d7ff9', icon: 'G' },
        { x: pad + spacing, y: cy, label: 'KAFKA', sub: 'Event Bus', color: '#8b5cf6', icon: 'K' },
        { x: pad + spacing * 2, y: cy, label: 'ORCHESTRATOR', sub: 'Rule Engine', color: '#f59e0b', icon: 'O' },
        { x: pad + spacing * 3, y: cy, label: 'CARLA', sub: 'Micro-sim', color: '#22c55e', icon: 'C' },
      ];
    };

    // Spawn particles on connections
    const spawnParticle = (from, to, color) => {
      particlesRef.current.push({
        x: from.x, y: from.y,
        tx: to.x, ty: to.y,
        progress: 0,
        speed: 0.008 + Math.random() * 0.006,
        color,
        size: 2 + Math.random() * 1.5,
      });
    };

    let spawnTimer = 0;

    const draw = () => {
      const rect = canvas.getBoundingClientRect();
      const w = rect.width;
      const h = rect.height;
      ctx.clearRect(0, 0, w, h);

      const nodes = getNodes();

      // Draw connections
      for (let i = 0; i < nodes.length - 1; i++) {
        const a = nodes[i];
        const b = nodes[i + 1];
        ctx.beginPath();
        ctx.moveTo(a.x + 22, a.y);
        ctx.lineTo(b.x - 22, b.y);
        ctx.strokeStyle = connected ? 'rgba(255,255,255,0.08)' : 'rgba(255,255,255,0.03)';
        ctx.lineWidth = 2;
        ctx.stroke();
      }

      // Spawn particles periodically
      if (connected) {
        spawnTimer++;
        if (spawnTimer % 8 === 0) {
          // GAMA → Kafka
          if (throughput.gama > 0 || spawnTimer % 16 === 0) {
            spawnParticle(nodes[0], nodes[1], '#2d7ff9');
          }
          // Kafka → Orchestrator
          spawnParticle(nodes[1], nodes[2], '#8b5cf6');
          // Orchestrator → CARLA
          if (throughput.commands > 0 || spawnTimer % 24 === 0) {
            spawnParticle(nodes[2], nodes[3], '#f59e0b');
          }
          // CARLA → Kafka (feedback)
          if (throughput.carla > 0 || spawnTimer % 20 === 0) {
            spawnParticle(nodes[3], nodes[1], '#22c55e');
          }
        }
      }

      // Update and draw particles
      particlesRef.current = particlesRef.current.filter(p => {
        p.progress += p.speed;
        if (p.progress >= 1) return false;

        const x = p.x + (p.tx - p.x) * p.progress;
        const y = p.y + (p.ty - p.y) * p.progress + Math.sin(p.progress * Math.PI * 3) * 3;

        ctx.beginPath();
        ctx.arc(x, y, p.size, 0, Math.PI * 2);
        ctx.fillStyle = p.color;
        ctx.globalAlpha = 0.7 * (1 - Math.pow(p.progress - 0.5, 2) * 4);
        ctx.fill();
        ctx.globalAlpha = 1;

        return true;
      });

      // Draw nodes
      nodes.forEach((node, i) => {
        // Background circle
        ctx.beginPath();
        ctx.arc(node.x, node.y, 22, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(17, 22, 32, 0.95)';
        ctx.fill();
        ctx.strokeStyle = connected ? node.color + '60' : 'rgba(255,255,255,0.06)';
        ctx.lineWidth = 1.5;
        ctx.stroke();

        // Icon letter
        ctx.fillStyle = connected ? node.color : 'rgba(255,255,255,0.2)';
        ctx.font = '600 14px Inter, sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(node.icon, node.x, node.y);

        // Status dot
        ctx.beginPath();
        ctx.arc(node.x + 16, node.y - 16, 4, 0, Math.PI * 2);
        ctx.fillStyle = connected ? '#22c55e' : '#ef4444';
        ctx.fill();

        // Label
        ctx.fillStyle = 'rgba(139, 149, 165, 0.9)';
        ctx.font = '600 10px Inter, sans-serif';
        ctx.fillText(node.label, node.x, node.y + 36);

        // Sublabel
        ctx.fillStyle = 'rgba(90, 99, 116, 0.8)';
        ctx.font = '400 9px Inter, sans-serif';
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
      style={{ width: '100%', height: 180, display: 'block' }}
    />
  );
}
