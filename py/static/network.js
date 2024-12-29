// @ts-check

import { parseSensorData } from './types.js';

/** @typedef {(data: import('./types.js').SensorData) => void} DataCallback */
/** @typedef {() => void} CloseCallback */

/**
 * Converts an ArrayBuffer containing binary float data to a Float32Array using big-endian byte order
 * @param {ArrayBuffer} buffer - The buffer containing big-endian encoded 32-bit float values
 * @returns {Float32Array} Array of parsed float values
 * @throws {Error} If buffer length is not a multiple of 4 bytes
 */
function parseBigEndianFloat32Array(buffer) {
  if (buffer.byteLength % 4 !== 0) {
    throw new Error('Buffer length must be a multiple of 4 bytes');
  }

  const dataView = new DataView(buffer);
  const floatCount = buffer.byteLength / 4;
  const parsedFloats = new Float32Array(floatCount);

  for (let i = 0; i < floatCount; i++) {
    parsedFloats[i] = dataView.getFloat32(i * 4, false);
  }

  return parsedFloats;
}

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
      const rawData = parseBigEndianFloat32Array(
          await event.data.arrayBuffer()
      );
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