// Raw store screenshots from the running web app (phone 412×732 at 2.625 = 1080×1920). Usage: node capture.mjs <base>
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url));
const BASE = process.argv[2] || 'http://localhost:3300';
const SHOTS = (process.argv[3] === 'duel') ? [['duel-live', '/duel', null]] : (process.argv[3] === 'q') ? [
  ['q-mcq', '/try', ['.try-cell:nth-child(2)']],
  ['q-image', '/try', ['.try-cell:nth-child(4)']],
  ['q-emoji', '/try', ['.try-cell:nth-child(3)']],
  ['q-map', '/try', ['.try-cell:nth-child(6)']],
] : [
  ['home', '/', null],
  ['categories', '/categories', null],
  ['try', '/try', null],
  ['duel', '/duel', null],
  ['games', '/games', null],
  ['carrom', '/games/carrom', ['.gk-mode']],
  ['domino', '/games/domino', null],
  ['foosball', '/games/foosball', ['.gk-mode']],
  ['kites', '/games/kites', ['.gk-mode']],
  ['seega', '/games/seega', null],
];
const port = 9570; const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
fs.rmSync(path.join(here, '.edge'), { recursive: true, force: true });
const edge = spawn('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', ['--headless=new', '--use-angle=d3d11', '--enable-gpu', '--ignore-gpu-blocklist', '--hide-scrollbars',
  `--remote-debugging-port=${port}`, `--user-data-dir=${path.join(here, '.edge')}`, 'about:blank'], { stdio: 'ignore' });
let ver; for (let i = 0; i < 100 && !ver; i++) { try { ver = await (await fetch(`http://127.0.0.1:${port}/json/version`)).json(); } catch { await sleep(200); } }
const ws = new WebSocket(ver.webSocketDebuggerUrl); await new Promise((r) => ws.addEventListener('open', r));
let id = 0; const pend = new Map();
ws.addEventListener('message', (e) => { const m = JSON.parse(e.data); if (m.id && pend.has(m.id)) { pend.get(m.id)(m); pend.delete(m.id); } });
const send = (method, params = {}, sessionId) => new Promise((r) => { const i = ++id; pend.set(i, r); ws.send(JSON.stringify({ id: i, method, params, sessionId })); });
const tid = (await send('Target.createTarget', { url: 'about:blank' })).result.targetId;
const sid = (await send('Target.attachToTarget', { targetId: tid, flatten: true })).result.sessionId;
await send('Page.enable', {}, sid); await send('Runtime.enable', {}, sid);
await send('Emulation.setDeviceMetricsOverride', { width: 412, height: 732, deviceScaleFactor: 2.625, mobile: true }, sid);
await send('Emulation.setUserAgentOverride', { userAgent: 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36 SahrahApp/1.0' }, sid);
const ev = async (x) => (await send('Runtime.evaluate', { expression: x, returnByValue: true, awaitPromise: true }, sid)).result?.result?.value;
await send('Page.navigate', { url: BASE + '/' }, sid); await sleep(3000);
await ev(`localStorage.setItem('sahrah.adsConsent','basic'); localStorage.setItem('sahrah.trackConsent','no'); localStorage.setItem('sahrah.seen','1'); localStorage.setItem('sahrah.games.muted','1'); sessionStorage.setItem('sahrah.splash','1'); 1`);
for (const [name, pg, clicks] of SHOTS) {
  await send('Page.navigate', { url: BASE + pg }, sid); await sleep(6500);
  for (const sel of clicks ?? []) { await ev(`(() => { const e = document.querySelector(${JSON.stringify(sel)}); e?.click(); return !!e; })()`); await sleep(5500); }
  const r = await send('Page.captureScreenshot', { format: 'png' }, sid);
  fs.writeFileSync(path.join(here, 'raw', `${name}.png`), Buffer.from(r.result.data, 'base64'));
}
console.log('done'); edge.kill(); ws.close(); process.exit(0);
