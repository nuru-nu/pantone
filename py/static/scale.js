// @ts-check

/**
 * @typedef {'linear' | 'log'} ScaleType
 */

/**
 * Class for tracking running statistics and dynamically scaling values
 */
export class DynamicScaler {
  /**
   * @param {Object} config Initial configuration
   * @param {string} config.name Name
   * @param {string} config.color Color specification
   * @param {number} config.min Initial minimum value
   * @param {number} config.max Initial maximum value
   * @param {ScaleType} [config.scaleType='linear'] Type of scaling function
   * @param {HTMLElement} config.element DOM element to render stats
   */
  constructor({ name, color, min, max, scaleType = 'linear', element }) {
    this.name = name;
    this.color = color;
    this.min = min;
    this.max = max;
    this.scaleType = scaleType;
    this.element = element;

    // Running statistics
    this.count = 0;
    this.sum = 0;
    this.sumSquares = 0;
    this.value = NaN;

    this.#updateStats();
  }

  /**
   * Add a new value and update statistics
   * @param {number} value New value to add
   */
  addValue(value) {
    this.value = value;
    this.count++;
    this.sum += value;
    this.sumSquares += value * value;

    this.min = Math.min(this.min, value);
    this.max = Math.max(this.max, value);

    this.#updateStats();
  }

  /**
   * Scale a value to range [0,1]
   * @param {number} value Value to scale
   * @returns {number} Scaled value between 0 and 1
   */
  scale(value) {
    if (this.scaleType === 'log') {
      return this.#logScale(value);
    }
    return this.#linearScale(value);
  }

  /**
   * Linear scaling function
   */
  #linearScale(value) {
    return (value - this.min) / (this.max - this.min);
  }

  /**
   * Logarithmic scaling function
   */
  #logScale(value) {
    const logMin = Math.log(Math.max(this.min, Number.EPSILON));
    const logMax = Math.log(this.max);
    return (Math.log(Math.max(value, Number.EPSILON)) - logMin) / (logMax - logMin);
  }

  /**
   * Calculate and update statistics display
   */
  #updateStats() {
    const avg = this.count ? this.sum / this.count : 0;
    const variance = this.count ? (this.sumSquares / this.count) - (avg * avg) : 0;
    const std = Math.sqrt(Math.max(0, variance));

    const pad = x => {
      return x.toFixed(2).padStart(6);
    };

    this.element.innerHTML = `<div style="color:${this.color}">` +
`${this.name} ${pad(this.value)} ⌀${pad(avg)}±${pad(std)}
[${this.scaleType.substring(0, 3)} ${pad(this.min)}..${pad(this.max)}]</div>`;
  }
}
