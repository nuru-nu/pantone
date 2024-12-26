// @ts-check

/**
 * @typedef {Object} State
 * @property {string} started
 * @property {('xy_hue'|'xz_hue'|'yz_hue')} algorithm
 * @property {number} param1
 */

const INITIAL_STATE = {started: '?', algorithm: 'xy_hue', param1: 0};

class StateManager {
  /**
   * @param {HTMLElement} targetElement
   */
  constructor(targetElement) {
    this.targetElement = targetElement;
    this.state = INITIAL_STATE;
    this.ws = null;
    this.initialize();
  }

  async initialize() {
    this.setupWebSocket();
  }

  setupWebSocket() {
    const protocol = {
        "http:": "ws:",
        "https:": "wss:",
    }[location.protocol];
    this.ws = new WebSocket(`${protocol}//${window.location.host}/state`);

    this.ws.onmessage = async (event) => {
      try {
        /** @type {State} */
        const state = JSON.parse(await event.data.text());
        this.updateState(state);
        this.render();
      } catch (error) {
        console.error('Failed to process WebSocket message:', error);
      }
    };

    this.ws.onerror = (error) => {
      console.error('WebSocket error:', error);
    };

    this.ws.onclose = () => {
      setTimeout(() => this.setupWebSocket(), 5000);
    };
  }

  /**
   * @param {State} stateUpdate
   */
  updateState(stateUpdate) {
    Object.assign(this.state, stateUpdate);
    this.render();
  }

  /**
   * @param {keyof State} key
   * @param {string|number} value
   */
  async updateStateKey(key, value) {
    try {
      const response = await fetch('/state', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ [key]: value }),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
    } catch (error) {
      console.error('Failed to update state:', error);
    }
  }

  render() {
    this.targetElement.innerHTML = `
      <div class="state-manager">
        <div class="state-item">
          <label>Timestamp:</label>
          <span>${this.state.started}</span>
        </div>

        <div class="state-item">
          <label>Algorithm:</label>
          <select id="algorithm">
            ${['xy_hue', 'xz_hue', 'yz_hue']
              .map(alg => `
                <option value="${alg}" ${this.state.algorithm === alg ? 'selected' : ''}>
                  ${alg}
                </option>
              `)
              .join('')}
          </select>
        </div>

        <div class="state-item">
          <label>Param1:</label>
          <input
            type="range"
            id="param1"
            min="0"
            max="1"
            step="0.01"
            value="${this.state.param1}"
          />
          <span>${this.state.param1.toFixed(2)}</span>
        </div>
      </div>
    `;

    // Add event listeners
    const algorithmSelect = this.targetElement.querySelector('#algorithm');
    const param1Input = this.targetElement.querySelector('#param1');

    if (algorithmSelect) {
      algorithmSelect.addEventListener('change', (e) => {
        const target = /** @type {HTMLSelectElement} */ (e.target);
        this.updateStateKey('algorithm', target.value);
      });
    }

    if (param1Input) {
      param1Input.addEventListener('input', (e) => {
        const target = /** @type {HTMLInputElement} */ (e.target);
        this.updateStateKey('param1', parseFloat(target.value));
      });
    }
  }
}

// Export the module
export default StateManager;