// @ts-check

/**
 * @typedef {Object} State
 * @property {string} started
 * @property {String[]} clients
 * @property {String} active
 * @property {number} alpha
 * @property {number} brightness
 * @property {String} device
 * @property {String} algorithm
 * @property {number} param1
 * @property {number} param2
 * @property {number} param3
 */

const INITIAL_STATE = {
  started: '?',
  clients: [], active: '',
  alpha: 1.0, brightness: 1.0,
  device: '?',
  algorithm: '?', param1: 1.0, param2: 1.0, param3: 1.0,
};

const ALGORITHMS = ['gx_gy', 'z_rot'];
const DEVICES = ['froggy', 'eurolite'];

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
    const slider = name => `
        <div class="state-item">
          <label>${name}:</label>
          <input
            type="range"
            id="${name}"
            min="0"
            max="1"
            step="0.01"
            value="${this.state[name]}"
          />
          <span>${this.state[name].toFixed(2)}</span>
        </div>
    `;
    const dropdown = (name, choices) => `
        <div class="state-item">
          <label>${name}:</label>
          <select id="${name}">
            ${choices
              .map(value => `
                <option value="${value}" ${this.state[name] === value ? 'selected' : ''}>
                  ${value}
                </option>
              `)
              .join('')}
          </select>
        </div>
    `;
    this.targetElement.innerHTML = `
      <div class="state-manager">
        <div class="state-item">
          <label>timestamp:</label>
          <span>${this.state.started}</span>
        </div>

        <div class="state-item">
          <label>active:</label>
          <span>${this.state.active}</span>
        </div>

        ${slider('alpha')}
        ${slider('brightness')}

        ${dropdown('device', DEVICES)}

        ${dropdown('algorithm', ALGORITHMS)}
        ${slider('param1')}
        ${slider('param2')}
        ${slider('param3')}

      </div>
    `;

    for(const id of ['device', 'algorithm']) {
      const select = this.targetElement.querySelector(`#${id}`);

      if (select) {
        select.addEventListener('change', (e) => {
          const target = /** @type {HTMLSelectElement} */ (e.target);
          this.updateStateKey(/** @type {keyof State} */ (id), target.value);
        });
      }
    }

    for(const id of ['alpha', 'brightness', 'param1', 'param2', 'param3']) {
      const el = this.targetElement.querySelector(`#${id}`);
      if (el) {
        el.addEventListener('input', (e) => {
          const target = /** @type {HTMLInputElement} */ (e.target);
          this.updateStateKey(/** @type {keyof State} */ (id), parseFloat(target.value));
        });
      }
    }
  }
}

// Export the module
export default StateManager;