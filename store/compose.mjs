// Store images from the raw screenshots: a gold headline + the screen in a rounded frame (1080×1920), the Play feature
// graphic (1024×500) and the 512 icon. Rendered as HTML in headless Edge so Arabic shapes correctly.
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url));
const SET = [
  ['01-home', 'home', 'سهرة أسئلة وضحك مع صحابك', 'فرق ضد فرق على موبايل واحد أو أونلاين'],
  ['02-image', 'q-emoji', 'صور وأسئلة عن مصر وأيامها', 'أكل الشارع، الأفلام، الكورة، والتاريخ'],
  ['03-emoji', 'q-image', 'إيموجي وصح ولا غلط', 'كل سؤال بطريقة مختلفة'],
  ['04-categories', 'categories', 'أكتر من ١٩٠ فئة', 'اختاروا اللي بتحبوه، وكل سهرة غير التانية'],
  ['05-duel', 'duel-live', '١ ضد ١ مع صاحبك', 'ابعتله لينك، أو العب مع صحاب جداد'],
  ['06-try', 'try', 'جرّب في دقيقة', 'من غير تسجيل، العب على طول'],
];
const css = `@import url('https://fonts.googleapis.com/css2?family=Lalezar&family=Alexandria:wght@500;700&display=swap');
html,body{margin:0;width:1080px;height:1920px;overflow:hidden}
body{background:radial-gradient(120% 60% at 50% 0%,#3a2a8a,#120d24 60%),#120d24;font-family:Alexandria,sans-serif;color:#fff;direction:rtl}
.wrap{position:absolute;inset:0;display:grid;justify-items:center;align-content:start;padding-top:110px}
h1{margin:0 60px;font-family:Lalezar,serif;font-weight:400;font-size:92px;line-height:1.15;text-align:center;color:#FFC93C;text-shadow:0 6px 0 #7a4b00}
p{margin:18px 60px 0;font-size:40px;font-weight:700;text-align:center;color:#E3DDFF}
.phone{margin-top:70px;width:760px;height:1351px;border-radius:64px;overflow:hidden;border:10px solid #2b2260;box-shadow:0 0 0 3px rgba(255,201,60,.55),0 40px 90px rgba(0,0,0,.6)}
.phone img{width:100%;height:100%;object-fit:cover;object-position:top}`;
const port = 9571; const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
fs.rmSync(path.join(here, '.edge2'), { recursive: true, force: true });
const edge = spawn('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', ['--headless=new', '--allow-file-access-from-files', '--hide-scrollbars',
  `--remote-debugging-port=${port}`, `--user-data-dir=${path.join(here, '.edge2')}`, 'about:blank'], { stdio: 'ignore' });
let ver; for (let i = 0; i < 100 && !ver; i++) { try { ver = await (await fetch(`http://127.0.0.1:${port}/json/version`)).json(); } catch { await sleep(200); } }
const ws = new WebSocket(ver.webSocketDebuggerUrl); await new Promise((r) => ws.addEventListener('open', r));
let id = 0; const pend = new Map();
ws.addEventListener('message', (e) => { const m = JSON.parse(e.data); if (m.id && pend.has(m.id)) { pend.get(m.id)(m); pend.delete(m.id); } });
const send = (method, params = {}, sessionId) => new Promise((r) => { const i = ++id; pend.set(i, r); ws.send(JSON.stringify({ id: i, method, params, sessionId })); });
const tid = (await send('Target.createTarget', { url: 'about:blank' })).result.targetId;
const sid = (await send('Target.attachToTarget', { targetId: tid, flatten: true })).result.sessionId;
await send('Page.enable', {}, sid);
async function render(html, file, w, h) {
  const f = path.join(here, '.tmp.html'); fs.writeFileSync(f, html);
  await send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: 1, mobile: false }, sid);
  await send('Page.navigate', { url: pathToFileURL(f).href }, sid); await sleep(2500);
  const r = await send('Page.captureScreenshot', { format: 'png' }, sid); fs.writeFileSync(file, Buffer.from(r.result.data, 'base64'));
}
for (const [out, raw, h1, sub] of SET) {
  const img = pathToFileURL(path.join(here, 'raw', `${raw}.png`)).href;
  await render(`<!doctype html><html lang="ar"><head><meta charset="utf-8"><style>${css}</style></head><body><div class="wrap"><h1>${h1}</h1><p>${sub}</p><div class="phone"><img src="${img}"></div></div></body></html>`,
    path.join(here, 'android', `phone-${out}.png`), 1080, 1920);
}
const logo = pathToFileURL(path.join(here, '..', 'assets', 'icon-only.png')).href;
await render(`<!doctype html><html lang="ar"><head><meta charset="utf-8"><style>@import url('https://fonts.googleapis.com/css2?family=Lalezar&family=Alexandria:wght@700&display=swap');
html,body{margin:0;width:1024px;height:500px;overflow:hidden}body{background:radial-gradient(90% 120% at 75% 30%,#4a35b0,#120d24 70%);direction:rtl;font-family:Alexandria,sans-serif;display:flex;align-items:center;gap:48px;padding:0 70px;box-sizing:border-box}
img{width:300px;height:300px;border-radius:64px;box-shadow:0 0 0 4px rgba(255,201,60,.6),0 24px 50px rgba(0,0,0,.55)}
h1{margin:0;font-family:Lalezar,serif;font-weight:400;font-size:112px;color:#FFC93C;text-shadow:0 6px 0 #7a4b00;line-height:1.1}p{margin:14px 0 0;font-size:36px;color:#E3DDFF}</style></head>
<body><img src="${logo}"><div><h1>سهرة وتسالي</h1><p>أسئلة وضحك مع صحابك</p></div></body></html>`, path.join(here, 'android', 'feature-graphic-1024x500.png'), 1024, 500);
fs.rmSync(path.join(here, '.tmp.html'), { force: true });
console.log('done'); edge.kill(); ws.close(); process.exit(0);
