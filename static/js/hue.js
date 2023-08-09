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

    const opts = {strategy: 'sendone'};

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

    async function sendnext(path) {
        if (inflight.has(path)) return;
        const queue = queues.get(path);
        if (!queue || !queue.length) return;
        const idx = queue.length - 1;
        if (idx) {
            dropped += idx;
        }
        inflight.add(path);
        await queue[idx]();
        queue.splice(0, idx + 1);
        inflight.delete(path);
        sendnext();
    }

    async function sendone(path, cb) {
        if (!queues.has(path)) queues.set(path, []);
        queues.get(path).push(cb);
        sendnext(path);
    }

    let fpsps = {};
    function fpst() {
        Object.values(fpsps).forEach(cb => cb());
        fpsps = {};
        window.setTimeout(fpst, 100);
    }
    fpst();
    async function fps(path, cb) {
        fpsps.hasOwnProperty(path) && dropped++;
        fpsps[path] = cb;
    }

    async function put(path, body) {
        ({sendone, fps})[opts.strategy](
            path,
            async () => {
                log(`PUT ${path} ${JSON.stringify(body)}`);
                const url = `http://${ip}/api/${hueser}/${path}`;
                const t0 = Date.now();
                const resp = await fetch(
                    url, {
                    method: 'PUT',
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
        
            }
        );
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
        const tot = dropped + dts.length;
        return (
            `sent ${dts.length}/${tot} (dropped ${rd(100 * dropped / tot)}%) –– ` +
            `dt[ms] ${rd(mean(dts))}±${rd(std(dts))} ` +
            `last=${rd(dts[dts.length - 1])} max=${rd(Math.max.apply(null, dts))}`
        );
    }

    return {
        set, state, put, get, stats, opts,
    };
}