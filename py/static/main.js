// @ts-check

// NETWORKING

function websocket() {
  const protocol = {
      "http:": "ws:",
      "https:": "wss:",
  }[location.protocol];
  return new WebSocket(`${protocol}//${location.host}/data`);
}
const ws = websocket();
const dataDiv = /** @type {HTMLDivElement} */ (document.getElementById('data'))

let lastData = new Float32Array(6), lastT = null, lastDt = 50;
ws.onmessage = async function(event) {
  const data = new Float32Array(await event.data.arrayBuffer());
  dataDiv.textContent = Array.from(data).map(n => n.toFixed(3)).join(', ');
  lastData = data;
  const t = performance.now();
  if (lastT) lastDt = Math.max(10, t - lastT);
  lastT = t;
};

ws.onclose = function() {
  dataDiv.textContent = 'Connection closed';
};

// UI

import StateManager from './state.js';
const stateManager = new StateManager(/** @type {HTMLElement} */ (document.getElementById('state')));
let bg = false;
// @ts-ignore
document.getElementById('bg').addEventListener('change', (event) => {
    // @ts-ignore
    bg = event.target.checked;
});


// DRAWING

import { DynamicScaler } from './scale.js';

const canvas = /** @type {HTMLCanvasElement} */ (document.getElementById('canvas'));
const ctx = /** @type {CanvasRenderingContext2D} */ (canvas.getContext('2d', { alpha: false }));

const offscreenCanvas = document.createElement('canvas');
const offscreenCtx = /** @type {CanvasRenderingContext2D} */ (offscreenCanvas.getContext('2d', { alpha: false }));

const scalersDiv = /** @type {HTMLDivElement} */ (document.getElementById('scalers'))
const createScaler = args => {
  const element = document.createElement('div');
  scalersDiv.append(element);
  return new DynamicScaler({...args, element});
};
const scalers = {
    gx: createScaler({name: 'gx', color: '#f00', min: -10, max: 10}),
    gy: createScaler({name: 'gy', color: '#0f0', min: -10, max: 10}),
    gz: createScaler({name: 'gz', color: '#00f', min: -10, max: 10}),
    hz: createScaler({name: 'hz', color: '#fff', min: 0, max: 60}),
};

function resizeCanvas() {
  // @ts-ignore
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

  const [gx, gy, gz, r, g, b] = lastData;

  ctx.fillStyle = `rgb(${Math.round(r * 255)}, ${Math.round(g * 255)}, ${Math.round(b * 255)})`;
  // @ts-ignore
  document.body.style.background = bg ? ctx.fillStyle : null;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(offscreenCanvas,
      1, 0, canvas.width - 1, canvas.height,  // src
      0, 0, canvas.width - 1, canvas.height,  // dst
  );

  const x = canvas.width - 1;
  function set(y, color) {
    y *= canvas.height;
    ctx.strokeStyle = 'black';
    ctx.beginPath();
    ctx.moveTo(x, y - 5);
    ctx.lineTo(x, y + 5);
    ctx.stroke();
    ctx.strokeStyle = color;
    ctx.beginPath();
    ctx.moveTo(x, y - 2);
    ctx.lineTo(x, y + 2);
    ctx.stroke();
  }

  scalers.gx.addValue(gx);
  scalers.gy.addValue(gy);
  scalers.gz.addValue(gz);
  set(scalers.gx.scale(gx), scalers.gx.color);
  set(scalers.gy.scale(gy), scalers.gy.color);
  set(scalers.gz.scale(gz), scalers.gz.color);

  const hz = 1 / (lastDt / 1e3);
  scalers.hz.addValue(hz);
  set(scalers.hz.scale(hz), scalers.hz.color);

  requestAnimationFrame(draw);
}

requestAnimationFrame(draw);
