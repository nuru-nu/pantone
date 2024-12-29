// @ts-check

import { NetworkManager } from './network.js';
import { Plot } from './plot.js';
import StateManager from './state.js';

const network = new NetworkManager();
const stateManager = new StateManager(/** @type {HTMLElement} */ (document.getElementById('state')));
const plotsDiv = /** @type {HTMLDivElement} */ (document.getElementById('plots'));

/** @type {Map<String, Plot>} */
const plots = new Map();

network.onData(data => {
  const name = stateManager.state.clients[data[1]];
  if (!name) return;
  if (!plots.has(name)) {
    plots.set(name, new Plot(name, plotsDiv));
  }
  /** @type {Plot} */ (plots.get(name)).addData(data);
});

network.onClose(() => {
  console.log('Connection closed');
});
