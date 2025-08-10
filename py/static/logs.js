// @ts-check

class Logs {
  /**
   * @param {HTMLElement} targetElement
   */
  constructor(targetElement) {
    this.targetElement = targetElement;
    this.targetElement.innerHTML = `
      <div class="logs">
      </div>
    `;
    this.logs = /** @type {HTMLElement} */ (this.targetElement.querySelector('.logs'));
    const css = document.createElement('style');
    css.textContent = `
      .logs {
        font-family: monospace;
      }
    `;
    document.head.appendChild(css);
  }

  /**
   * @param {String} message
   */
  add(message) {
    console.log('message', message);
    const el = document.createElement('div');
    el.textContent = message;
    this.logs.insertBefore(el, this.logs.firstChild);
  }
}

// Export the module
export default Logs;
