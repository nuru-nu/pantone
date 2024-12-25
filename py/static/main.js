// NETWORKING

function websocket() {
  const protocol = {
      "http:": "ws:",
      "https:": "wss:",
  }[location.protocol];
  return new WebSocket(`${protocol}//${location.host}/ws`);
}
const ws = websocket();
const dataDiv = document.getElementById('data');

let lastData = null, lastT = null, lastDt = null;
ws.onmessage = async function(event) {
  const data = new Float32Array(await event.data.arrayBuffer());
  dataDiv.textContent = Array.from(data).map(n => n.toFixed(3)).join(', ');
  lastData = data;
  const t = performance.now();
  if (lastT) lastDt = t - lastT;
  lastT = t;
};

ws.onclose = function() {
  dataDiv.textContent = 'Connection closed';
};

// DRAWING

const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d', { alpha: false });

const offscreenCanvas = document.createElement('canvas');
const offscreenCtx = offscreenCanvas.getContext('2d', { alpha: false });

function resizeCanvas() {
  const width = canvas.parentElement.clientWidth;
  const height = canvas.height;
  canvas.width = width;
  offscreenCanvas.width = canvas.width;
  offscreenCanvas.height = canvas.height;

  ctx.fillStyle = 'black';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  offscreenCtx.fillStyle = 'black';
  offscreenCtx.fillRect(0, 0, canvas.width, canvas.height);
}

resizeCanvas();
window.addEventListener('resize', resizeCanvas);

function getNewData() {
  return Math.random() * canvas.height;
}

function draw(timestamp) {

  offscreenCtx.drawImage(canvas, 0, 0);

  ctx.fillStyle = 'black';
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  ctx.drawImage(offscreenCanvas,
      1, 0, canvas.width - 1, canvas.height,  // src
      0, 0, canvas.width - 1, canvas.height,  // dst
  );

  const x = canvas.width - 1;
  function set(y, color) {
    ctx.strokeStyle = color;
    ctx.beginPath();
    ctx.moveTo(x, y - 1);
    ctx.lineTo(x, y + 1);
    ctx.stroke();
  }

  if (lastData) {
    const [gx, gy, ..._] = lastData;
    const norm = y => Math.floor((y / 20 + 0.5) * canvas.height);
    set(norm(gx), 'red');
    set(norm(gy), 'green');
  }
  if (lastDt) {
    const norm = y => Math.floor((y / 100) * canvas.height);
    const clip = y => Math.max(5, Math.min(canvas.height, y));
    set(clip(norm(lastDt)), 'white');
  }

  requestAnimationFrame(draw);
}

requestAnimationFrame(draw);
