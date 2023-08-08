import {log} from './utils.js';

function delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function mean(array) {
    const sum = array.reduce((acc, value) => acc + value, 0);
    return sum / array.length;
}

function std(array) {
    const mu = mean(array);
    const squaredDifferences = array.map(value => Math.pow(value - mu, 2));
    const variance = squaredDifferences.reduce((acc, value) => acc + value, 0) / array.length;
    return Math.sqrt(variance);
}

export default function(ip, hueser) {

    const queues = new Map();
    const inflight = new Set();
    let dropped = 0, dts = [];

    async function get(path) {
        const url = `http://${ip}/api/${hueser}/${path}`;
        const resp = await fetch(
            url, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
            },
        });
        if (!resp.ok) throw new Error(`resp not okay! ${resp}`);
        return await resp.json();
    }

    async function sendone(path) {
        if (inflight.has(path)) return;
        const queue = queues.get(path);
        if (!queue || !queue.length) return;
        const idx = queue.length - 1;
        if (idx) {
            dropped += idx;
        }
        inflight.add(path);
        const {method, body} = queue[idx];
        log(`${method} ${path} ${JSON.stringify(body)}`);
        const url = `http://${ip}/api/${hueser}/${path}`;
        const t0 = Date.now();
        const resp = await fetch(
            url, {
            method,
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(body),
        });
        const dt = Date.now() - t0;
        dts.push(dt);
        if (!resp.ok) throw new Error(`resp not okay! ${resp}`);
        const data = await resp.json();
        log(`${dt}ms – ${JSON.stringify(data)}`);
        queue.splice(0, idx + 1);
        inflight.delete(path);
        sendone();
    }

    async function put(path, body) {
        if (!queues.has(path)) queues.set(path, []);
        queues.get(path).push({method: 'PUT', body});
        sendone(path);
    }

    async function set(i, on) {
        return put(`lights/${i}/state`, {on, transitiontime: 0});
    }

    async function state() {
        const url = `http://${ip}/api/${hueser}`;
        const resp = await fetch(url);
        if (!resp.ok) throw new Error(`resp not okay! ${resp}`);
        return await resp.json();
    }

    function stats() {
        const rd = x => Math.round(100 * x) / 100;
        return (
            `sent ${dts.length} dropped ${rd(100*dropped/(dropped+dts.length))}% ` +
            `dt=${rd(mean(dts))}±${rd(std(dts))}`
        );
    }

    return {
        set, state, put, get, stats,
    };
}