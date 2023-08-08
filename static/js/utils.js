
const pre = document.querySelector('#log');

function ts() {
    const d = new Date();
    const pad = n => n.toString().padStart(2, '0');
    return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export function log(message) {
    pre.textContent = ts() + ' ' + message + '\n\n' + pre.textContent;
}

export function onerror(handler) {
    function onerror(e) {
      console.log(e);
      handler(e.error || e.reason);
    }
  
    window.addEventListener("error", onerror);
    window.addEventListener("unhandledrejection", onerror);
  }
  