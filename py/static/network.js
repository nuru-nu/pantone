// @ts-check

import { parseSensorData } from './types.js';

/** @typedef {(data: import('./types.js').SensorData) => void} DataCallback */
/** @typedef {() => void} CloseCallback */

/** @class */
export class NetworkManager {
  /** @type {DataCallback[]} */
  #dataCallbacks = [];
  /** @type {CloseCallback[]} */
  #closeCallbacks = [];
  /** @type {WebSocket} */
  #ws;

  constructor() {
    this.#ws = this.#createWebSocket();
    this.setupHandlers();
  }

  /** @returns {WebSocket} */
  #createWebSocket() {
    const protocol = {
      "http:": "ws:",
      "https:": "wss:",
    }[location.protocol];
    return new WebSocket(`${protocol}//${location.host}/data`);
  }

  setupHandlers() {
    this.#ws.onmessage = async (event) => {
      const rawData = new Float32Array(await event.data.arrayBuffer());
      const data = parseSensorData(rawData);
      this.#dataCallbacks.forEach(cb => cb(data));
    };

    this.#ws.onclose = () => {
      this.#closeCallbacks.forEach(cb => cb());
    };
  }

  /** @param {DataCallback} callback */
  onData(callback) {
    this.#dataCallbacks.push(callback);
  }

  /** @param {CloseCallback} callback */
  onClose(callback) {
    this.#closeCallbacks.push(callback);
  }
}