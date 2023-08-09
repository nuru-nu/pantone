import Textfield from './components/textfield.js';
import Hue from './hue.js';
import {log, onerror} from './utils.js';
import {div} from './components/html.js';

onerror(log);

const control = document.querySelector('#control');
const live = document.querySelector('#live');
const state = document.querySelector('#state');
const dump = document.querySelector('#dump');
const put_path = document.querySelector('#put_path');
const put_body = document.querySelector('#put_body');

const ip = '192.168.0.119';
const hueser = 'AEOfkTiZbCyKeG5Ms0o0APh7T2bmtzu1V14S9UEd';

const hue = Hue(ip, hueser);

const lights = [];

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

function stats() {
    document.querySelector('#stats').textContent = hue.stats();
    window.setTimeout(stats, 300);
}
stats();

const strategy = document.querySelector('#strategy');
hue.opts.strategy = strategy.value;
strategy.addEventListener('change', () => hue.opts.strategy = strategy.value);

async function init() {
    const data = await hue.get('lights');
    const names_lights = [];
    for (const [id, value] of Object.entries(data)) {
        const light = div('light ' + (value.state.on ? 'on ' : ''));
        light.textContent = id;
        lights.push(light);
        light.addEventListener('click', () => {
            light.classList.toggle('on');
            hue.set(id, light.classList.contains('on'));
        });
        names_lights.push([value.name, light]);
    }
    names_lights.sort();
    names_lights.forEach(([_, light]) =>
        document.querySelector('#lights').append(light));

    put_path.value = `lights/${lights[0].textContent}/state`;
    log('ready');
}
init();
