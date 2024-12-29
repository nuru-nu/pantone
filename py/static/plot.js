// @ts-check

import { DynamicScaler } from './scale.js';
import './types.js';

/** @typedef {Object} PlotConfig
 * @property {HTMLCanvasElement} canvas
 * @property {HTMLDivElement} scalersDiv
 * @property {boolean} [enableBackground]
 */

/** @class */
export class Plot {
  /** @type {HTMLCanvasElement} */
  #canvas;
  /** @type {CanvasRenderingContext2D} */
  #ctx;
  /** @type {HTMLCanvasElement} */
  #offscreenCanvas;
  /** @type {CanvasRenderingContext2D} */
  #offscreenCtx;
  /** @type {Record<string, import('./scale.js').DynamicScaler>} */
  #scalers;
  /** @type {import('./types.js').SensorData[]} */
  #datas = [];
  /** @type {number} */
  #lastT = 0;
  /** @type {boolean} */
  #enableBackground;

  /** @param {PlotConfig} config */
  constructor(config) {
    this.#canvas = config.canvas;
    this.#ctx = /** @type {CanvasRenderingContext2D} */ (this.#canvas.getContext('2d', { alpha: false }));
    this.#offscreenCanvas = document.createElement('canvas');
    this.#offscreenCtx = /** @type {CanvasRenderingContext2D} */ (this.#offscreenCanvas.getContext('2d', { alpha: false }));
    this.#enableBackground = config.enableBackground ?? false;

    this.#scalers = {
      gx: this.#createScaler(config.scalersDiv, {name: 'gx', color: '#f00', min: -10, max: 10}),
      gy: this.#createScaler(config.scalersDiv, {name: 'gy', color: '#0f0', min: -10, max: 10}),
      gz: this.#createScaler(config.scalersDiv, {name: 'gz', color: '#00f', min: -10, max: 10}),
      hz: this.#createScaler(config.scalersDiv, {name: 'hz', color: '#fff', min: 0, max: 60}),
    };

    this.resizeCanvas();
    window.addEventListener('resize', () => this.resizeCanvas());
    requestAnimationFrame(timestamp => this.draw(timestamp));
  }

  /**
   * @param {HTMLDivElement} parent
   * @param {Object} args
   */
  #createScaler(parent, args) {
    const element = document.createElement('div');
    parent.append(element);
    return new DynamicScaler({...args, element});
  }

  resizeCanvas() {
    const width = this.#canvas.parentElement?.clientWidth ?? this.#canvas.width;
    this.#canvas.width = width;
    this.#offscreenCanvas.width = width;
    this.#offscreenCanvas.height = this.#canvas.height;

    this.#ctx.fillStyle = 'black';
    this.#ctx.fillRect(0, 0, this.#canvas.width, this.#canvas.height);
    this.#offscreenCtx.fillStyle = 'black';
    this.#offscreenCtx.fillRect(0, 0, this.#canvas.width, this.#canvas.height);
  }

  /**
   * @param {import('./types.js').SensorData} data
   */
  addData(data) {
    this.#datas.push(data);
  }

  /**
   * @param {number} timestamp
   */
  draw(timestamp) {

    this.#offscreenCtx.drawImage(this.#canvas, 0, 0);

    this.#ctx.drawImage(this.#offscreenCanvas,
      1, 0, this.#canvas.width - 1, this.#canvas.height,
      0, 0, this.#canvas.width - 1, this.#canvas.height,
    );

    const n = this.#datas.length;
    console.log(n);
    if (n) {
      const [t, gx, gy, gz, r, g, b] = this.#datas[n - 1];

      this.#datas = [];

      this.#ctx.fillStyle = `rgb(${Math.round(r * 255)}, ${Math.round(g * 255)}, ${Math.round(b * 255)})`;
      this.#ctx.fillRect(this.#canvas.width - 1, 0, this.#canvas.width, this.#canvas.height);
      this.#drawGraphs(t, gx, gy, gz);

      if (this.#enableBackground) {
        document.body.style.background = this.#ctx.fillStyle;
      }
    }

    requestAnimationFrame(timestamp => this.draw(timestamp));
  }

  /**
   * @param {number} t
   * @param {number} gx
   * @param {number} gy
   * @param {number} gz
   */
  #drawGraphs(t, gx, gy, gz) {
    const x = this.#canvas.width - 1;
    const dtMs = Math.max(10, (t - this.#lastT));
    const hz = 1 / (dtMs / 1e3);
    this.#lastT = t;

    this.#scalers.gx.addValue(gx);
    this.#scalers.gy.addValue(gy);
    this.#scalers.gz.addValue(gz);
    this.#scalers.hz.addValue(hz);

    const set = (y, color) => {
      y *= -this.#canvas.height;
      y += this.#canvas.height;
      this.#ctx.strokeStyle = 'black';
      this.#ctx.beginPath();
      this.#ctx.moveTo(x, y - 5);
      this.#ctx.lineTo(x, y + 5);
      this.#ctx.stroke();
      this.#ctx.strokeStyle = color;
      this.#ctx.beginPath();
      this.#ctx.moveTo(x, y - 2);
      this.#ctx.lineTo(x, y + 2);
      this.#ctx.stroke();
    };

    set(this.#scalers.gx.scale(gx), this.#scalers.gx.color);
    set(this.#scalers.gy.scale(gy), this.#scalers.gy.color);
    set(this.#scalers.gz.scale(gz), this.#scalers.gz.color);
    set(this.#scalers.hz.scale(hz), this.#scalers.hz.color);
  }

  /** @param {boolean} enable */
  setBackgroundEnabled(enable) {
    this.#enableBackground = enable;
    if (!enable) {
      document.body.style.background = '';
    }
  }
}
