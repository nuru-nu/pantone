// @ts-check

import { NetworkManager } from './network.js';
import { Plot } from './plot.js';
import StateManager from './state.js';

const network = new NetworkManager();
const stateManager = new StateManager(/** @type {HTMLElement} */ (document.getElementById('state')));
const dataDiv = /** @type {HTMLDivElement} */ (document.getElementById('data'));

const plot = new Plot({
  canvas: /** @type {HTMLCanvasElement} */ (document.getElementById('canvas')),
  scalersDiv: /** @type {HTMLDivElement} */ (document.getElementById('scalers')),
  enableBackground: false
});

network.onData(data => {
  dataDiv.textContent = Array.from(data).map(n => n.toFixed(3)).join(', ');
  plot.setData(data);
});

network.onClose(() => {
  dataDiv.textContent = 'Connection closed';
});

// @ts-ignore
document.getElementById('bg').addEventListener('change', (event) => {
  // @ts-ignore
  plot.setBackgroundEnabled(event.target.checked);
});
