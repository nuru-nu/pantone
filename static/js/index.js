import Textfield from './components/textfield.js';
import Hue from './hue.js';
import {log} from './utils.js';

const control = document.querySelector('#control');
const live = document.querySelector('#live');
const state = document.querySelector('#state');
const dump = document.querySelector('#dump');
const put_path = document.querySelector('#put_path');
const put_body = document.querySelector('#put_body');

const ip = '192.168.0.119';
const hueser = 'ibFm3Ltg35wXp53IOGg2doChOrnfGLNCe6s7gQ2W';

const hue = Hue(ip, hueser);

const lights = [...document.querySelectorAll('.light')];

lights.forEach(el => {
    const idx = parseInt(el.textContent);
    el.addEventListener('click', () => {
        el.classList.toggle('on');
        hue.set(idx, el.classList.contains('on'));
    });
    hue.set(idx, el.classList.contains('on'));
});

dump.addEventListener('click', async () => {
    const state = await hue.state();
    log(JSON.stringify(state, null, 2));
});
state.addEventListener('click', async () => {
    const state = await hue.state();
    log('\n' + Object.entries(state.lights).map(([k, v]) => 
        `${k} - ${JSON.stringify(v.state)}`
    ).join('\n'));
});
live.addEventListener('click', async () => {
    live.classList.toggle('on');
});

function put() {
    hue.put(put_path.value, JSON.parse(put_body.value));
}
put_path.addEventListener('keyup', e => e.key === 'Enter' && put());
put_body.addEventListener('keyup', e => e.key === 'Enter' && put());

async function controlit(e) {
    const bcr = control.getBoundingClientRect();
    const x = (e.clientX - bcr.left) / bcr.width;
    const y = (e.clientY - bcr.top) / bcr.height;
    for(const light of lights) {
        if (!light.classList.contains('on')) continue;
        const idx = parseInt(light.textContent);
        const bri = Math.round(255 * y);
        const hue_ = Math.round(65535 * x);
        hue.put(`lights/${idx}/state`, {bri, hue: hue_, transitiontime: 0});
    }
}

control.addEventListener('click', controlit);
control.addEventListener('mousemove', e => live.classList.contains('on') && controlit(e));

log.textContent = 'ready';
