const WebSocket = require('ws');
const ws = new WebSocket('ws://localhost:6868');
ws.on('open', function open() {
  ws.send(JSON.stringify({
    "type": "expression",
    "exp_id": "0",
    "expr": "eval_gaml(\"ask world { BLOCKED_ROADS <- osmRoad overlapping (to_GAMA_CRS({1.4595,43.5528}, 'EPSG:4326') + 25.0#m); }\")"
  }));
});
ws.on('message', function incoming(data) {
  console.log('Received:', data.toString());
  // Don't close immediately, wait for other messages!
  setTimeout(() => ws.close(), 2000);
});
