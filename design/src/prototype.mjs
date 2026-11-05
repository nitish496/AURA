// AURA 2.0 HTML prototype builder. `node design/src/build.mjs` writes design/prototype.html.
// Every frame is 390 x 844 (dp == CSS px). Fonts are the exact files bundled in the app.
// All content is placeholder: invented artists and titles, typographic artwork, original lyrics.

import { ICONS, STROKE } from './icons.mjs';
import { markPaths } from './brand.mjs';
import { color } from './tokens.mjs';

// ------------------------------------------------------------------ primitives
const icon = (name, size = 24, extra = '') => {
  const layers = ICONS[name].map((l) => {
    const st = `stroke="currentColor" stroke-width="${STROKE}" stroke-linecap="round" stroke-linejoin="round"`;
    if (l.mode === 's') return `<path d="${l.d}" fill="none" ${st}/>`;
    if (l.mode === 'f') return `<path d="${l.d}" fill="currentColor"/>`;
    if (l.mode === 'fe') return `<path d="${l.d}" fill="currentColor" fill-rule="evenodd"/>`;
    return `<path d="${l.d}" fill="currentColor" ${st}/>`;
  }).join('');
  return `<svg class="ic" ${extra} width="${size}" height="${size}" viewBox="0 0 24 24" aria-hidden="true">${layers}</svg>`;
};
const mark = (size, fill = 'currentColor') =>
  `<svg width="${size}" height="${size}" viewBox="0 0 48 48" aria-hidden="true">${markPaths(1).map((d) => `<path d="${d}" fill="${fill}"/>`).join('')}</svg>`;
const abs = (x, y, w, h) => `left:${x}px;top:${y}px;${w != null ? `width:${w}px;` : ''}${h != null ? `height:${h}px;` : ''}`;
const radiusFor = (size) => (size <= 48 ? 6 : size <= 96 ? 8 : size <= 200 ? 10 : 12);

// ------------------------------------------------------------------ typographic sleeves
// Each sleeve is drawn in "em" units where 1em = 1% of the sleeve width.
const SLEEVES = {
  lowtide: { t: 'Low Tide', a: 'Field Theory', bg: '#1F4B49', mesh: ['#2E6B66', '#0F2624', '#8FB3A3', '#173836'],
    html: `<i style="left:8em;right:8em;top:58em;height:.8em;background:#E9E2D0;opacity:.85"></i>
      <b style="left:8em;bottom:7em;font:700 15em/.86 InterD;letter-spacing:-.03em;color:#E9E2D0">low<br>tide</b>
      <b style="left:8em;top:8em;font:600 4.4em/1 Inter;letter-spacing:.16em;color:#E9E2D0;opacity:.75">FIELD THEORY</b>` },
  lanterns: { t: 'Paper Lanterns', a: 'Mira Sol', bg: '#C98B2B',
    html: `<i style="right:9em;top:10em;width:44em;height:44em;border-radius:50%;background:#16120C"></i>
      <b style="left:8em;bottom:8em;font:700 10.5em/.95 InterD;letter-spacing:-.02em;color:#16120C">Paper<br>Lanterns</b>
      <b style="left:8em;top:9em;font:600 4.2em/1 Inter;letter-spacing:.12em;color:#16120C">MIRA SOL</b>` },
  nightbus: { t: 'Night Bus Home', a: 'The Quiet Hours', bg: '#4A1B1C',
    html: `<i style="left:9em;top:9em;width:.7em;height:52em;background:#EBD9C4;opacity:.5"></i>
      <i style="left:13em;top:9em;width:.7em;height:40em;background:#EBD9C4;opacity:.5"></i>
      <i style="left:17em;top:9em;width:.7em;height:28em;background:#EBD9C4;opacity:.5"></i>
      <b style="right:8em;bottom:8em;text-align:right;font:700 11em/.9 InterD;letter-spacing:-.01em;color:#EBD9C4">NIGHT<br>BUS<br>HOME</b>
      <b style="left:9em;bottom:9em;font:600 3.8em/1.2 Inter;letter-spacing:.1em;color:#EBD9C4;opacity:.7">THE QUIET<br>HOURS</b>` },
  saltwater: { t: 'Saltwater', a: 'Ana Reyes', bg: '#D9CDB5',
    html: `<i style="left:0;right:0;bottom:0;height:34em;background:#1D2B3A"></i>
      <b style="left:8em;top:8em;font:700 13em/1 InterD;letter-spacing:-.03em;color:#1D2B3A">Saltwater</b>
      <b style="left:8em;top:24em;font:500 4.6em/1 Inter;color:#1D2B3A;opacity:.8">Ana Reyes</b>` },
  concrete: { t: 'Concrete Garden', a: 'Grey Atlas', bg: '#4E5930',
    html: `<i style="left:8em;top:8em;width:84em;height:50em;background-image:radial-gradient(circle,#C9CF9E 0 1.1em,transparent 1.3em);background-size:12em 12em;opacity:.45"></i>
      <b style="left:8em;bottom:8em;font:700 9.5em/.95 InterD;letter-spacing:-.02em;color:#ECEBD8">Concrete<br>Garden</b>` },
  northern: { t: 'Northern Line', a: 'Kofi Mensah', bg: '#2D4963',
    html: `<i style="left:-10em;top:48em;width:130em;height:.9em;background:#E8E4DA;transform:rotate(-38deg);opacity:.9"></i>
      <b style="left:8em;top:8em;font:700 11em/.92 InterD;letter-spacing:-.02em;color:#E8E4DA">Northern<br>Line</b>
      <b style="right:8em;bottom:8em;font:600 4.2em/1 Inter;letter-spacing:.14em;color:#E8E4DA;opacity:.8">KOFI MENSAH</b>` },
  golden: { t: 'Golden Hour Tapes', a: 'Sunday Club', bg: '#B4521F',
    html: `<i style="left:0;right:0;bottom:8em;height:4em;background:#F1E4CF"></i><i style="left:0;right:0;bottom:16em;height:4em;background:#F1E4CF;opacity:.7"></i><i style="left:0;right:0;bottom:24em;height:4em;background:#F1E4CF;opacity:.4"></i>
      <b style="left:8em;top:8em;font:700 10em/.95 InterD;letter-spacing:-.02em;color:#F1E4CF">Golden<br>Hour Tapes</b>` },
  bloom: { t: 'Static Bloom', a: 'Wire & Wool', bg: '#213A2B',
    html: `<i style="right:-22em;bottom:-22em;width:80em;height:80em;border-radius:50%;border:.9em solid rgba(214,226,200,.28)"></i><i style="right:-8em;bottom:-8em;width:52em;height:52em;border-radius:50%;border:.9em solid rgba(214,226,200,.4)"></i>
      <b style="left:8em;top:8em;font:700 11em/.92 InterD;letter-spacing:-.02em;color:#D6E2C8">Static<br>Bloom</b>` },
  monsoon: { t: 'Monsoon Letters', a: 'Aanya Rao', bg: '#8A3A22',
    html: `<i style="inset:0;background-image:repeating-linear-gradient(90deg,rgba(244,226,210,.16) 0 .6em,transparent .6em 7em);mask-image:linear-gradient(#000,transparent 70%);-webkit-mask-image:linear-gradient(#000,transparent 70%)"></i>
      <b style="left:8em;bottom:8em;font:700 10em/.95 InterD;letter-spacing:-.02em;color:#F4E2D2">Monsoon<br>Letters</b>` },
  glasshouses: { t: 'Glass Houses', a: 'Harbor & Pine', bg: '#EAE3D3',
    html: `<i style="left:22em;top:14em;width:56em;height:56em;border:1em solid #2A2A2A"></i>
      <b style="left:8em;bottom:8em;font:700 8.5em/1 InterD;letter-spacing:-.01em;color:#2A2A2A">Glass Houses</b>` },
  signal: { t: 'Signal Fires', a: 'Oskar Lind', bg: '#2A2928',
    html: `<b style="left:6em;top:4em;font:700 30em/.8 InterD;letter-spacing:-.05em;color:#3B3936">SF</b>
      <b style="left:8em;bottom:8em;font:700 10em/.95 InterD;letter-spacing:-.02em;color:#EDE6DA">Signal<br>Fires</b>` },
  bluehour: { t: 'Blue Hour', a: 'Lena Moreau', bg: '#1E4552',
    html: `<i style="left:20em;top:-12em;width:72em;height:72em;border-radius:50%;background:#2C6273"></i>
      <b style="left:8em;bottom:8em;font:700 12em/.9 InterD;letter-spacing:-.03em;color:#E3EEF0">Blue<br>Hour</b>` },
  afterimage: { t: 'Afterimage', a: 'Nadia Karim', bg: '#C7A02A',
    html: `<i style="left:14em;top:12em;width:42em;height:42em;border:1em solid #17140A"></i><i style="left:30em;top:26em;width:42em;height:42em;background:#17140A"></i>
      <b style="left:8em;bottom:7em;font:700 9em/1 InterD;letter-spacing:-.02em;color:#17140A">Afterimage</b>` },
  tidal: { t: 'Tidal Lines', a: 'Juno Park', bg: '#8B8E87',
    html: `<i style="left:8em;right:8em;top:14em;height:44em;background-image:repeating-linear-gradient(180deg,#22241F 0 .8em,transparent .8em 7em);opacity:.8"></i>
      <b style="left:8em;bottom:8em;font:700 10em/1 InterD;letter-spacing:-.02em;color:#1C1E1A">Tidal Lines</b>` },
  cassette: { t: 'Cassette Weather', a: 'Motel Radio', bg: '#9A3B2D',
    html: `<b style="left:6em;top:6em;font:700 17em/.82 InterD;letter-spacing:-.04em;color:#F3E3D6">CASS<br>ETTE<br>WEA<br>THER</b>` },
  // Catalogue playlists
  latenight: { t: 'Late Night Drives', a: 'Nightshift Collective', bg: '#1B2E35',
    html: `<i style="left:8em;right:8em;bottom:30em;height:.8em;background:#D8E4E2;opacity:.6"></i><i style="left:8em;width:30em;bottom:30em;height:.8em;background:#D8E4E2"></i>
      <b style="left:8em;bottom:8em;font:700 10em/.95 InterD;letter-spacing:-.02em;color:#D8E4E2">Late Night<br>Drives</b>
      <b style="left:8em;top:8em;font:600 4em/1 Inter;letter-spacing:.14em;color:#D8E4E2;opacity:.7">PLAYLIST</b>` },
  sunday: { t: 'Sunday Reset', a: 'Porchlight', bg: '#CDBB9F',
    html: `<i style="left:8em;top:8em;width:30em;height:30em;border-radius:50%;background:#3A2F22"></i>
      <b style="left:8em;bottom:8em;font:700 11em/.92 InterD;letter-spacing:-.02em;color:#3A2F22">Sunday<br>Reset</b>` },
  focus: { t: 'Deep Focus', a: 'Quiet Rooms', bg: '#2B3A2E',
    html: `<i style="left:8em;right:8em;top:8em;height:46em;border:.8em solid rgba(220,230,214,.5)"></i>
      <b style="left:8em;bottom:8em;font:700 11em/.92 InterD;letter-spacing:-.02em;color:#DCE6D6">Deep<br>Focus</b>` },
  warehouse: { t: 'Warehouse Hours', a: 'Low Ceiling', bg: '#3A3530',
    html: `<i style="left:8em;top:8em;width:84em;height:40em;background-image:repeating-linear-gradient(90deg,#F0E6D8 0 2.4em,transparent 2.4em 7em);opacity:.3"></i>
      <b style="left:8em;bottom:8em;font:700 9.5em/.95 InterD;letter-spacing:-.02em;color:#F0E6D8">Warehouse<br>Hours</b>` },
};
// Sleeve markup is written with "em" = 1% of the sleeve; convert to container units so
// elements that set their own font-size still position against the sleeve.
const cq = (html) => html.replace(/([a-z-]+)\s*:\s*([^;"]+)/g, (m, prop, val) =>
  prop === 'letter-spacing' ? m : `${prop}:${val.replace(/(-?\d*\.?\d+)em\b/g, '$1cqw')}`);
// AURA mixes: one template, coloured from the top track's artwork.
const MIXES = {
  heavy: { t: 'Heavy Rotation', bg: '#183B39', sub: 'Your most played, refreshed daily' },
  rediscover: { t: 'Rediscover', bg: '#5B281C', sub: 'Songs you loved last year' },
  newfinds: { t: 'New Finds', bg: '#243C52', sub: 'From the Audius catalogue' },
  latemix: { t: 'Late Night', bg: '#232326', sub: 'Quiet songs from your library' },
};
const mixArt = (id, size) => {
  const m = MIXES[id];
  return `<div class="art" style="width:${size}px;height:${size}px;border-radius:${radiusFor(size)}px;background:${m.bg}">${cq(`
    <b style="left:8em;top:8em;display:flex;align-items:center;gap:2.4em;font:600 4.6em/1 Inter;letter-spacing:.12em;color:#F5F5F7;opacity:.85">${mark(Math.max(8, size * 0.075), '#F5F5F7')}MIX</b>
    <b style="left:8em;right:8em;bottom:8em;font:700 11.5em/.92 InterD;letter-spacing:-.025em;color:#F5F5F7">${m.t.replace(' ', '<br>')}</b>`)}</div>`;
};
// Tracks without artwork in the placeholder library.
const NO_ART = { smallhours: 'Small Hours' };
const art = (id, size, r) => {
  if (MIXES[id]) return mixArt(id, size);
  if (!SLEEVES[id]) return placeholder(NO_ART[id] || id, size, r);
  const s = SLEEVES[id];
  return `<div class="art" style="width:${size}px;height:${size}px;border-radius:${r ?? radiusFor(size)}px;background:${s.bg}">${cq(s.html)}</div>`;
};
// Missing artwork: initials on graphite with a subtle, deterministic tonal shift.
const TONES = ['#1C1C1F', '#202023', '#242427', '#1A1A1D'];
const initials = (t) => t.replace(/^(the|a)\s+/i, '').split(/\s+/).slice(0, 2).map((w) => w[0]).join('').toUpperCase();
const hash = (t) => [...t].reduce((h, c) => (h * 31 + c.charCodeAt(0)) >>> 0, 7);
function placeholder(title, size, r, circle = false) {
  const tone = TONES[hash(title) % TONES.length];
  return `<div class="art ph" style="width:${size}px;height:${size}px;border-radius:${circle ? '50%' : `${r ?? radiusFor(size)}px`};font-size:${size / 100}px;background:${tone}">
    <b style="inset:0;display:grid;place-items:center;font:700 34em/1 InterD;letter-spacing:-.02em;color:#6A6A70">${initials(title)}</b></div>`;
}
const avatar = (name, size) => placeholder(name, size, 0, true);

// ------------------------------------------------------------------ chrome
const statusBar = (light = false) => `<div class="status ${light ? 'on-art' : ''}"><span class="num">9:41</span><span class="sys">
  <svg width="17" height="12" viewBox="0 0 17 12"><rect x="0" y="8" width="3" height="4" rx="1"/><rect x="4.5" y="5.5" width="3" height="6.5" rx="1"/><rect x="9" y="3" width="3" height="9" rx="1"/><rect x="13.5" y="0" width="3" height="12" rx="1"/></svg>
  <svg width="16" height="12" viewBox="0 0 16 12"><path d="M8 11.5 10.6 8.6A3.7 3.7 0 0 0 5.4 8.6Z M8 1C5.1 1 2.6 2.1.7 3.9L2.3 5.6A8.2 8.2 0 0 1 13.7 5.6L15.3 3.9C13.4 2.1 10.9 1 8 1Z M8 4.9A6.2 6.2 0 0 0 3.6 6.8L5.1 8.4A4.1 4.1 0 0 1 10.9 8.4L12.4 6.8A6.2 6.2 0 0 0 8 4.9Z"/></svg>
  <svg width="26" height="12" viewBox="0 0 26 12"><rect x=".6" y=".6" width="22" height="10.8" rx="3.2" fill="none" stroke="currentColor" stroke-width="1.2" opacity=".5"/><rect x="2.4" y="2.4" width="15" height="7.2" rx="1.8"/><rect x="23.8" y="4" width="1.6" height="4" rx=".8" opacity=".5"/></svg></span></div>`;
const gesture = () => `<div class="gesture"></div>`;
const glassBtn = (x, y, name, size = 40, extra = '') =>
  `<div class="glass circle btn" style="${abs(x, y, size, size)}${extra}">${icon(name, size >= 44 ? 24 : 22)}</div>`;

const TABS = [['home', 'Home'], ['browse', 'Browse'], ['library', 'Library']];
function tabBar(active, { search = false } = {}) {
  const items = TABS.map(([k, label]) => {
    const on = k === active;
    return `<div class="tab ${on ? 'on' : ''}">${on ? `<i class="lens"></i>` : ''}${icon(on ? `${k}_fill` : k, 24)}<span>${label}</span></div>`;
  }).join('');
  return `<div class="glass capsule tabbar" style="${abs(16, 752, 286, 64)}">${items}</div>
    <div class="glass circle btn search ${search ? 'on' : ''}" style="${abs(310, 752, 64, 64)}">${icon('search', 24)}</div>`;
}
const NOW = { art: 'lowtide', t: 'Low Tide', a: 'Field Theory' };
function miniPlayer(y = 688, now = NOW, playing = true) {
  return `<div class="glass capsule mini" style="${abs(16, y, 358, 56)}">
    ${art(now.art, 40, 8)}
    <div class="mini-text"><div class="sh w500 c1x ell">${now.t}</div><div class="fn c2x ell">${now.a}</div></div>
    <div class="mini-btn">${icon(playing ? 'pause' : 'play', 26)}</div><div class="mini-btn">${icon('next', 24)}</div></div>`;
}
// Minimised chrome: tab bar collapses to the active tab, the mini player docks between.
function minimizedChrome(active, now = NOW) {
  return `<div class="glass circle btn tabmin" style="${abs(16, 768, 48, 48)}">${icon(`${active}_fill`, 24)}</div>
    <div class="glass capsule mini small" style="${abs(72, 768, 246, 48)}">
      ${art(now.art, 32, 6)}<div class="mini-text"><div class="fn w500 c1x ell">${now.t}</div></div>
      <div class="mini-btn">${icon('pause', 22)}</div><div class="mini-btn">${icon('next', 20)}</div></div>
    <div class="glass circle btn" style="${abs(326, 768, 48, 48)}">${icon('search', 22)}</div>`;
}
const largeTitle = (text, buttons = '') =>
  `<div class="lt c1x" style="${abs(16, 52)}">${text}</div>${buttons}`;
const header = (x, y, text, chevron = true, extra = '') =>
  `<div class="sec" style="${abs(x, y)}"><span class="t2 c1x">${text}</span>${chevron ? icon('chevron_right', 20, 'class="ic chev"') : ''}${extra}</div>`;

// ------------------------------------------------------------------ content blocks
function card(x, y, id, size, title, sub, opts = {}) {
  return `<div class="card" style="${abs(x, y, size)}">${opts.circle ? avatar(title, size) : art(id, size)}
    <div class="sh w500 c1x ell2" style="margin-top:8px;${opts.circle ? 'text-align:center' : ''}">${title}</div>
    ${sub ? `<div class="fn c2x ell" style="${opts.circle ? 'text-align:center' : ''}">${sub}</div>` : ''}</div>`;
}
function featureCard(x, y, eyebrow, id, title, sub, tint) {
  return `<div style="${abs(x, y, 272)}position:absolute"><div class="fn c2x" style="margin-bottom:8px">${eyebrow}</div>
    <div class="feature" style="background:${tint}">${art(id, 272, 0)}
    <div class="feature-foot"><div class="hl" style="color:#F5F5F7">${title}</div><div class="fn ell2" style="color:rgba(245,245,247,.72)">${sub}</div></div></div></div>`;
}
const SRC = {
  phone: icon('phone', 14, 'class="ic src"'), cloud: icon('cloud', 14, 'class="ic src"'), saved: icon('cloud_check', 14, 'class="ic src"'),
};
function songRow(y, { id, title, sub, src, state, trailing = 'more', explicit, x = 16, w = 358, num, dim, onArt }) {
  const playing = state === 'playing';
  const lead = num != null
    ? `<div class="num-col ${playing ? 'acc' : 'c3x'}">${playing ? eq() : num}</div>`
    : `<div style="position:relative">${art(id, 44, 6)}${playing ? `<div class="art-eq">${eq('#F5F5F7')}</div>` : ''}</div>`;
  const t = trailing === 'more' ? `<div class="row-more ${onArt ? 'on-art-2' : 'c2x'}">${icon('more', 20)}</div>`
    : trailing === 'drag' ? `<div class="row-more on-art-3">${icon('drag', 22)}</div>`
    : trailing;
  return `<div class="row ${dim ? 'dim' : ''}" style="${abs(x, y, w, num != null ? 52 : 60)}">${lead}
    <div class="row-text"><div class="rt ${playing ? 'acc' : onArt ? '' : 'c1x'} ell">${title}</div>
    <div class="fn ${onArt ? 'on-art-2' : 'c2x'} ell sub">${explicit ? `<span class="explicit">E</span>` : ''}${src ? SRC[src] : ''}${sub}</div></div>${t}</div>`;
}
const eq = (c) => `<span class="eqbars" ${c ? `style="color:${c}"` : ''}><i style="height:9px"></i><i style="height:14px"></i><i style="height:6px"></i></span>`;
function listRow(y, ic, label, value, { sep = true, accentIcon = true, chevron = true } = {}) {
  return `<div class="lrow" style="${abs(16, y, 358, 52)}"><div class="${accentIcon ? 'acc' : 'c2x'}">${icon(ic, 24)}</div>
    <div class="co c1x" style="flex:1">${label}</div>${value ? `<div class="co c3x num">${value}</div>` : ''}${chevron ? `<div class="c3x">${icon('chevron_right', 20)}</div>` : ''}
    ${sep ? '<i class="sep" style="left:40px"></i>' : ''}</div>`;
}
const pill = (label, ic, kind = 'primary', w) =>
  `<div class="pill ${kind}" ${w ? `style="width:${w}px"` : ''}>${ic ? icon(ic, 20) : ''}<span>${label}</span></div>`;
const chip = (label, on) => `<div class="chip ${on ? 'on' : ''}">${label}</div>`;
const toggle = (on) => `<div class="toggle ${on ? 'on' : ''}"><i></i></div>`;

// ------------------------------------------------------------------ screens
const S = {};

S.home = () => `${statusBar()}
  ${largeTitle('Home', glassBtn(334, 52, 'account'))}
  ${header(16, 108, 'Top Picks for You', false)}
  ${featureCard(16, 148, 'Made for You', 'heavy', 'Heavy Rotation', 'Low Tide, Paper Lanterns, Night Bus Home and more', '#123230')}
  ${featureCard(300, 148, 'Because you played Field Theory', 'bluehour', 'Blue Hour', 'Lena Moreau · Album', '#183843')}
  ${header(16, 560, 'Recently Played')}
  ${card(16, 600, 'lanterns', 152, 'Paper Lanterns', 'Mira Sol')}
  ${card(180, 600, 'latenight', 152, 'Late Night Drives', 'Playlist · Audius')}
  ${card(344, 600, 'nightbus', 152, 'Night Bus Home', 'The Quiet Hours')}
  ${miniPlayer()}${tabBar('home')}${gesture()}`;

S.homeScrolled = () => `${statusBar()}
  ${card(16, -28, 'lanterns', 152, 'Paper Lanterns', 'Mira Sol')}${card(180, -28, 'latenight', 152, 'Late Night Drives', 'Playlist · Audius')}${card(344, -28, 'nightbus', 152, 'Night Bus Home', 'The Quiet Hours')}
  ${header(16, 216, 'Made for You')}
  ${card(16, 256, 'heavy', 152, 'Heavy Rotation', 'Updated today')}${card(180, 256, 'rediscover', 152, 'Rediscover', 'Updated Monday')}${card(344, 256, 'newfinds', 152, 'New Finds', '')}
  ${header(16, 488, 'Trending on Audius')}<div class="fn c3x" style="${abs(16, 518)}">This week</div>
  ${songRow(548, { id: 'cassette', title: 'Overpass', sub: 'Motel Radio', src: 'cloud', num: undefined })}
  ${songRow(608, { id: 'golden', title: 'Lights Out, Windows Down', sub: 'Sunday Club', src: 'cloud', explicit: true })}
  ${songRow(668, { id: 'monsoon', title: 'Paper Planes Over Lagos', sub: 'Aanya Rao', src: 'cloud' })}
  ${songRow(728, { id: 'afterimage', title: 'Velour', sub: 'Nadia Karim', src: 'cloud' })}
  <div class="glass bar" style="${abs(0, 0, 390, 92)}"><div class="hl c1x" style="position:absolute;left:0;right:0;top:56px;text-align:center">Home</div></div>
  ${statusBar()}${glassBtn(338, 50, 'account', 36)}
  ${minimizedChrome('home')}${gesture()}`;

S.browse = () => `${statusBar()}
  ${largeTitle('Browse')}
  ${header(16, 108, 'Trending Playlists')}
  ${card(16, 148, 'latenight', 200, 'Late Night Drives', 'Nightshift Collective')}
  ${card(228, 148, 'focus', 200, 'Deep Focus', 'Quiet Rooms')}
  ${header(16, 428, 'Top Songs This Week')}
  ${[['cassette', 'Overpass', 'Motel Radio'], ['golden', 'Lights Out, Windows Down', 'Sunday Club'], ['monsoon', 'Paper Planes Over Lagos', 'Aanya Rao'], ['afterimage', 'Velour', 'Nadia Karim']]
    .map((r, i) => `<div class="rank num c2x" style="${abs(16, 468 + i * 60 + 19)}">${i + 1}</div>` + songRow(468 + i * 60, { id: r[0], title: r[1], sub: r[2], src: 'cloud', x: 40, w: 334, explicit: i === 1 })).join('')}
  ${header(16, 718, 'Genres', false)}
  ${[['Electronic', '#2D4963'], ['Hip-Hop & Rap', '#8A3A22'], ['Lo-Fi', '#4E5930'], ['Ambient', '#1F4B49']].map((g, i) =>
    `<div class="genre" style="${abs(16 + (i % 2) * 183, 758 + Math.floor(i / 2) * 104, 175, 96)}background:${g[1]}"><span class="hl">${g[0]}</span></div>`).join('')}
  ${miniPlayer()}${tabBar('browse')}${gesture()}`;

const searchBar = (query) => `<div class="glass circle btn" style="${abs(16, 768, 48, 48)}">${icon('home', 24)}</div>
  <div class="glass capsule field" style="${abs(72, 768, 302, 48)}">${icon('search', 20)}<span class="${query ? 'c1x' : 'c3x'} co">${query || 'Artists, songs, albums'}</span>${query ? `<span class="clear">${icon('close', 14)}</span>` : ''}</div>`;

S.search = () => `${statusBar()}
  ${largeTitle('Search')}
  <div class="sec" style="${abs(16, 112, 358)}justify-content:space-between"><span class="t3 c1x">Recently Searched</span><span class="sh acc">Clear</span></div>
  <div class="row" style="${abs(16, 148, 358, 60)}">${avatar('Field Theory', 44)}<div class="row-text"><div class="rt c1x">Field Theory</div><div class="fn c2x">Artist</div></div><div class="c3x">${icon('close', 18)}</div></div>
  ${songRow(208, { id: 'nightbus', title: 'Night Bus Home', sub: 'Album · The Quiet Hours', trailing: `<div class="c3x">${icon('close', 18)}</div>` })}
  ${songRow(268, { id: 'northern', title: 'Northern Line', sub: 'Song · Kofi Mensah', trailing: `<div class="c3x">${icon('close', 18)}</div>` })}
  <div class="row" style="${abs(16, 328, 358, 60)}"><div class="qicon c2x">${icon('search', 20)}</div><div class="row-text"><div class="rt c1x">monsoon letters</div></div><div class="c3x">${icon('close', 18)}</div></div>
  ${header(16, 412, 'Browse Categories', false)}
  ${[['Electronic', '#2D4963'], ['Hip-Hop & Rap', '#8A3A22'], ['Lo-Fi', '#4E5930'], ['Ambient', '#1F4B49'], ['Jazz', '#9C6F22'], ['Acoustic', '#6E716A']].map((g, i) =>
    `<div class="genre" style="${abs(16 + (i % 2) * 183, 452 + Math.floor(i / 2) * 104, 175, 96)}background:${g[1]}"><span class="hl">${g[0]}</span></div>`).join('')}
  ${miniPlayer(704)}${searchBar('')}${gesture()}`;

S.searchResults = () => `${statusBar()}
  <div class="chips" style="${abs(16, 52)}">${chip('All', true)}${chip('Songs')}${chip('Artists')}${chip('Albums')}${chip('Playlists')}${chip('Your Library')}</div>
  <div class="row top" style="${abs(16, 104, 358, 96)}">${art('nightbus', 80)}<div class="row-text"><div class="t3 c1x">Night Bus Home</div><div class="fn c2x" style="margin-top:2px">Album · The Quiet Hours</div>
    <div class="fn c3x sub" style="margin-top:6px">${SRC.phone}On this phone</div></div><div class="playcirc">${icon('play', 18)}</div></div>
  ${header(16, 224, 'Songs')}
  ${songRow(264, { id: 'nightbus', title: 'Night Bus Home', sub: 'The Quiet Hours', src: 'phone' })}
  ${songRow(324, { id: 'latenight', title: 'Nightswimming in Lagos', sub: 'Tunde Ade · Audius', src: 'cloud' })}
  ${songRow(384, { id: 'bloom', title: 'Late Night Static', sub: 'Wire & Wool · Saved', src: 'saved' })}
  ${songRow(444, { id: 'warehouse', title: 'Night Market', sub: 'Low Ceiling · Audius', src: 'cloud', explicit: true })}
  ${header(16, 524, 'Artists')}
  ${card(16, 564, null, 96, 'Night Owls', 'Artist', { circle: true })}${card(124, 564, null, 96, 'Nightshift Collective', 'Artist', { circle: true })}${card(232, 564, null, 96, 'Nina Haddad', 'Artist', { circle: true })}
  ${miniPlayer(704)}${searchBar('night')}${gesture()}`;

S.library = () => `${statusBar()}
  ${largeTitle('Library', glassBtn(334, 52, 'plus'))}
  ${listRow(108, 'playlist', 'Playlists', '14')}${listRow(160, 'artist', 'Artists', '302')}${listRow(212, 'album', 'Albums', '186')}${listRow(264, 'song', 'Songs', '1,248')}${listRow(316, 'cloud_check', 'Saved from Audius', '41', { sep: false })}
  ${header(16, 392, 'Collections', false)}
  ${[[['lowtide', 'lanterns', 'nightbus', 'northern'], 'Favourites', '96 songs'], [['lanterns', 'lowtide', 'golden', 'bluehour'], 'Most Played', 'Updated today'], [['saltwater', 'concrete', 'bloom', 'monsoon'], 'Recently Added', 'Last 30 days']].map((c, i) =>
    `<div class="card" style="${abs(16 + i * 164, 432, 152)}"><div class="mosaic">${c[0].map((id) => art(id, 76, 0)).join('')}</div>
     <div class="sh w500 c1x" style="margin-top:8px">${c[1]}</div><div class="fn c2x">${c[2]}</div></div>`).join('')}
  ${header(16, 664, 'Recently Added')}
  ${card(16, 704, 'saltwater', 175, 'Saltwater', 'Ana Reyes')}${card(199, 704, 'concrete', 175, 'Concrete Garden', 'Grey Atlas')}
  ${miniPlayer()}${tabBar('library')}${gesture()}`;

const TRACKS = [['Harbour Lights', '3:41'], ['Low Tide', '4:12'], ['Salt Air', '3:05'], ['Lantern Bay', '3:58'], ['Driftwood', '2:47'], ['Undertow', '4:30'], ['Shoreline', '3:22']];
S.album = (theme = 'dark') => `
  <div style="${abs(0, 0, 390, 390)}position:absolute">${art('lowtide', 390, 0)}</div><i class="top-scrim"></i>
  ${statusBar(true)}
  ${glassBtn(16, 48, 'back')}${glassBtn(286, 48, 'heart')}${glassBtn(334, 48, 'more')}
  <div class="t1 c1x" style="${abs(16, 406)}">Low Tide</div>
  <div class="t3 c2x" style="${abs(16, 442)}font-weight:500">Field Theory</div>
  <div class="fn c3x" style="${abs(16, 470)}">Ambient · 2025 · On this phone</div>
  <div class="pills" style="${abs(16, 504, 358)}">${pill('Play', 'play', 'primary', 175)}${pill('Shuffle', 'shuffle', 'secondary', 175)}</div>
  ${TRACKS.slice(0, 6).map((t, i) => songRow(572 + i * 52, { num: i + 1, title: t[0], sub: '', state: i === 1 ? 'playing' : null,
    trailing: `<div class="fn c3x num" style="margin-right:4px">${t[1]}</div><div class="row-more c2x">${icon('more', 20)}</div>`, explicit: i === 3 })).join('')}
  ${miniPlayer()}${tabBar('library')}${gesture()}`;

S.playlist = () => `${statusBar()}
  ${glassBtn(16, 48, 'back')}${glassBtn(334, 48, 'more')}
  <div style="${abs(16, 104)}position:absolute">${art('latenight', 148)}</div>
  <div style="${abs(180, 108, 194)}position:absolute"><div class="c2 c3x caps">Playlist · Audius</div><div class="t2 c1x" style="margin-top:6px">Late Night Drives</div>
    <div class="sh c2x" style="margin-top:4px">Nightshift Collective</div><div class="fn c3x num" style="margin-top:6px">32 songs · 1 hr 54 min</div></div>
  <div class="fn c2x" style="${abs(16, 268, 358)}">Slow electronic and lo-fi for the drive home. The curator updates it every Friday.</div>
  <div class="pills" style="${abs(16, 320, 358)}">${pill('Play', 'play', 'primary', 143)}${pill('Shuffle', 'shuffle', 'secondary', 143)}<div class="iconpill">${icon('plus', 22)}</div></div>
  ${songRow(388, { id: 'latenight', title: 'Nightswimming in Lagos', sub: 'Tunde Ade', src: 'cloud' })}
  ${songRow(448, { id: 'bloom', title: 'Late Night Static', sub: 'Wire & Wool', src: 'saved' })}
  ${songRow(508, { id: 'northern', title: 'Northern Line', sub: 'Kofi Mensah', src: 'phone' })}
  ${songRow(568, { id: 'warehouse', title: 'Night Market', sub: 'Low Ceiling', src: 'cloud', explicit: true })}
  ${songRow(628, { id: 'tidal', title: 'Undercurrent', sub: 'Juno Park', src: 'cloud' })}
  ${songRow(688, { id: 'cassette', title: 'Overpass', sub: 'Motel Radio', src: 'cloud' })}
  ${miniPlayer()}${tabBar('browse')}${gesture()}`;

S.artist = () => `
  <div class="artist-hero" style="${abs(0, 0, 390, 372)}"><b>FT</b><i class="hero-scrim"></i></div><i class="top-scrim"></i>
  ${statusBar(true)}${glassBtn(16, 48, 'back')}${glassBtn(334, 48, 'more')}
  <div class="lt" style="${abs(16, 280)}color:#F5F5F7">Field Theory</div>
  <div class="fn" style="${abs(16, 324)}color:rgba(245,245,247,.7)">48K followers on Audius</div>
  <div class="playfab" style="${abs(318, 288, 56, 56)}">${icon('play', 24)}</div>
  ${header(16, 392, 'Top Songs')}
  ${songRow(432, { id: 'lowtide', title: 'Low Tide', sub: 'Low Tide · 2025', state: 'playing' })}
  ${songRow(492, { id: 'lowtide', title: 'Harbour Lights', sub: 'Low Tide · 2025' })}
  ${songRow(552, { id: 'tidal', title: 'Breakwater', sub: 'Single · 2024', src: 'cloud' })}
  ${songRow(612, { id: 'bluehour', title: 'Coastline (Live)', sub: 'Live at the Pier · 2023', src: 'cloud' })}
  ${header(16, 692, 'Albums')}
  ${card(16, 732, 'lowtide', 152, 'Low Tide', '2025')}${card(180, 732, 'tidal', 152, 'Tidal Lines', '2023')}
  ${miniPlayer()}${tabBar('home')}${gesture()}`;

const mesh = (c) => `<div class="mesh" style="--m1:${c[0]};--m2:${c[1]};--m3:${c[2]};--m4:${c[3]}"><i></i><i></i><i></i><i></i></div>`;
// Same formula as ui/NowPlayingLayout.java (390 x 844 frame, 40dp status bar, 24dp gesture inset).
const NPL = (() => {
  const W = 390, H = 844, T = 40, B = 24, M = 24, HANDLE = 20, TITLE = 52, SCRUB = 32, TRANS = 72, CAP = 52;
  const MIN = [8, 28, 16, 16, 20, 12], WT = [0.12, 0.12, 0.08, 0.26, 0.26, 0.16];
  const avail = H - T - B, budget = avail - (HANDLE + TITLE + SCRUB + TRANS + CAP) - MIN.reduce((a, b) => a + b, 0);
  const art = Math.max(160, Math.min(520, Math.min(W - 2 * M, budget))), extra = Math.max(0, budget - art);
  const adds = WT.map((w) => Math.round(extra * w)), gaps = MIN.map((g, i) => g + adds[i]);
  gaps[5] += extra - adds.reduce((a, b) => a + b, 0);
  const handle = T, artY = handle + HANDLE + gaps[0], title = artY + art + gaps[1], scrub = title + TITLE + gaps[2];
  const transport = scrub + SCRUB + gaps[3], capsule = transport + TRANS + gaps[4];
  return { art, artX: (W - art) / 2, handle, artY, title, scrub, transport, capsule, gaps };
})();
const utility = (active) => `<div class="glass capsule util" style="${abs(99, NPL.capsule, 192, 52)}">
  ${['lyrics', 'output', 'queue'].map((n) => `<div class="ub ${n === active ? 'on' : ''}">${icon(n, 22)}</div>`).join('')}</div>`;
S.nowPlaying = () => `${mesh(SLEEVES.lowtide.mesh)}${statusBar(true)}
  <div class="grabber" style="top:${NPL.handle + 8}px"></div>
  <div class="np-art" style="${abs(NPL.artX, NPL.artY, NPL.art, NPL.art)}">${art('lowtide', NPL.art, 12)}</div>
  <div style="${abs(24, NPL.title + 2, 250)}position:absolute"><div class="t3" style="color:#F5F5F7">Low Tide</div><div class="t3 w400" style="color:rgba(245,245,247,.64);margin-top:2px">Field Theory</div></div>
  ${glassBtn(286, NPL.title + 8, 'heart_fill', 36, 'color:#F5F5F7')}${glassBtn(330, NPL.title + 8, 'more', 36)}
  <div class="scrub" style="${abs(24, NPL.scrub + 4, 342)}"><div class="track"><i style="width:34%"></i></div>
    <div class="times num"><span>1:24</span><span>−2:48</span></div></div>
  <div class="transport" style="${abs(24, NPL.transport, 342)}"><span>${icon('previous', 40)}</span><span>${icon('pause', 56)}</span><span>${icon('next', 40)}</span></div>
  ${utility(null)}${gesture()}`;

const LYRICS = ['We left the porch light on', 'so the boats could find their way', 'Every wave a little slower', 'every word a little softer',
  'Hold on, the tide is turning', 'hold on, it is coming in', 'Count the lights along the harbour'];
S.lyrics = () => `${mesh(SLEEVES.lowtide.mesh)}${statusBar(true)}
  <div class="np-head" style="${abs(24, 52, 342)}">${art('lowtide', 48, 6)}<div style="flex:1;min-width:0"><div class="hl" style="color:#F5F5F7">Low Tide</div><div class="sh" style="color:rgba(245,245,247,.64)">Field Theory</div></div></div>
  ${glassBtn(286, 56, 'heart_fill', 36)}${glassBtn(330, 56, 'more', 36)}
  <div class="lyrics" style="${abs(24, 132, 342)}">${LYRICS.map((l, i) => `<p class="${i === 2 ? 'on' : i < 2 ? 'past' : 'next'}">${l}</p>`).join('')}</div>
  ${utility('lyrics')}${gesture()}`;

S.queue = () => `${mesh(SLEEVES.lowtide.mesh)}${statusBar(true)}
  <div class="np-head" style="${abs(24, 52, 342)}">${art('lowtide', 48, 6)}<div style="flex:1;min-width:0"><div class="hl" style="color:#F5F5F7">Low Tide</div><div class="sh" style="color:rgba(245,245,247,.64)">Field Theory</div></div></div>
  ${glassBtn(330, 56, 'more', 36)}
  <div style="${abs(24, 128)}position:absolute"><div class="hl" style="color:#F5F5F7">Playing Next</div><div class="fn" style="color:rgba(245,245,247,.64)">From Low Tide</div></div>
  <div class="qtoggles" style="${abs(222, 126)}"><span>${icon('shuffle', 20)}</span><span class="on">${icon('repeat', 20)}</span><span class="on">${icon('autoplay', 20)}</span></div>
  ${songRow(184, { id: 'lowtide', title: 'Salt Air', sub: 'Field Theory', trailing: 'drag', onArt: true, x: 24, w: 342 })}
  <div class="lifted" style="${abs(16, 240, 358, 64)}">${songRow(2, { id: 'lanterns', title: 'Lantern Bay', sub: 'Mira Sol', trailing: 'drag', onArt: true, x: 8, w: 342 })}</div>
  ${songRow(308, { id: 'lowtide', title: 'Driftwood', sub: 'Field Theory', trailing: 'drag', onArt: true, x: 24, w: 342 })}
  ${songRow(368, { id: 'lowtide', title: 'Undertow', sub: 'Field Theory', trailing: 'drag', onArt: true, x: 24, w: 342 })}
  ${songRow(428, { id: 'bluehour', title: 'Coastline', sub: 'Lena Moreau', trailing: 'drag', onArt: true, x: 24, w: 342 })}
  <div style="${abs(24, 508)}position:absolute"><div class="hl" style="color:#F5F5F7">Autoplay</div><div class="fn" style="color:rgba(245,245,247,.64)">Similar songs play when your queue ends</div></div>
  ${songRow(564, { id: 'tidal', title: 'Breakwater', sub: 'Juno Park · Audius', trailing: 'more', onArt: true, x: 24, w: 342, dim: true })}
  ${songRow(624, { id: 'saltwater', title: 'Saltwater', sub: 'Ana Reyes', trailing: 'more', onArt: true, x: 24, w: 342, dim: true })}
  ${utility('queue')}${gesture()}`;

S.menu = () => `<div class="under">${S.album()}</div><div class="scrim"></div>
  <div class="lifted solid" style="${abs(16, 226, 358, 60)}">${songRow(0, { num: 4, title: 'Lantern Bay', sub: '', explicit: true, x: 0, w: 358,
    trailing: `<div class="fn c3x num" style="margin-right:16px">3:58</div>` })}</div>
  <div class="glass menu" style="${abs(16, 296, 260)}">
    ${[['Play Next', 'play_next'], ['Play Last', 'add_to_queue'], ['Add to Playlist…', 'playlist_add'], ['Favourite', 'heart']].map((m) => `<div class="mi"><span>${m[0]}</span>${icon(m[1], 22)}</div>`).join('')}
    <div class="msep"></div>
    ${[['Go to Album', 'album'], ['Go to Artist', 'artist'], ['Share…', 'share']].map((m) => `<div class="mi"><span>${m[0]}</span>${icon(m[1], 22)}</div>`).join('')}
  </div>${gesture()}`;

function eqGraph() {
  const bands = [3, 1.5, -1, 1, 2.5]; // dB
  const W = 326, H = 150, x0 = 16, pts = bands.map((g, i) => [x0 + 60 + i * ((W - 72) / 4), H / 2 - (g / 12) * (H / 2 - 12)]);
  let d = `M${pts[0][0]},${pts[0][1]}`;
  for (let i = 1; i < pts.length; i++) { const [ax, ay] = pts[i - 1], [bx, by] = pts[i], mx = (ax + bx) / 2; d += ` C${mx},${ay} ${mx},${by} ${bx},${by}`; }
  const grid = [12, 0, -12].map((db) => { const y = H / 2 - (db / 12) * (H / 2 - 12); return `<line x1="${x0 + 44}" x2="${x0 + W}" y1="${y}" y2="${y}" stroke="var(--sep)" stroke-width="1"/><text x="${x0}" y="${y + 4}" class="svgcap">${db > 0 ? '+' : ''}${db} dB</text>`; }).join('');
  return `<svg width="358" height="${H}" viewBox="0 0 358 ${H}">${grid}<path d="${d}" fill="none" stroke="var(--accent)" stroke-width="2.5" stroke-linecap="round"/>
    ${pts.map((p) => `<circle cx="${p[0]}" cy="${p[1]}" r="7" fill="var(--t1)"/>`).join('')}</svg>`;
}
S.sound = () => `${statusBar()}
  ${glassBtn(16, 48, 'back')}<div class="hl c1x" style="position:absolute;left:0;right:0;top:57px;text-align:center">Sound</div>
  <div class="group" style="${abs(16, 104, 358, 300)}">
    <div class="grow"><span class="co c1x" style="flex:1">Equaliser</span>${toggle(true)}</div><i class="sep" style="left:16px;top:52px"></i>
    <div class="chips" style="position:absolute;left:16px;top:64px">${chip('Flat')}${chip('Bass')}${chip('Vocal', true)}${chip('Acoustic')}${chip('Custom')}</div>
    <div style="position:absolute;left:0;top:108px">${eqGraph()}</div>
    <div class="bands c2 c3x num" style="position:absolute;left:64px;right:22px;top:266px"><span>60</span><span>230</span><span>910</span><span>3.6k</span><span>14k Hz</span></div>
  </div>
  <div class="fn c3x caps" style="${abs(32, 424)}">Playback</div>
  <div class="group" style="${abs(16, 446, 358, 228)}">
    <div class="grow tall"><div style="flex:1"><div class="co c1x">Loudness normalisation</div><div class="fn c2x">Plays every song at a similar volume</div></div>${toggle(true)}</div><i class="sep" style="left:16px;top:64px"></i>
    <div class="grow" style="top:64px"><span class="co c1x" style="flex:1">Crossfade</span><span class="co c2x num">6 s</span></div>
    <div class="scrub mini-scrub" style="position:absolute;left:16px;right:16px;top:116px"><div class="track"><i style="width:50%;background:var(--accent)"></i><b style="left:50%"></b></div></div><i class="sep" style="left:16px;top:144px"></i>
    <div class="grow" style="top:144px"><span class="co c1x" style="flex:1">Gapless playback</span>${toggle(true)}</div><i class="sep" style="left:16px;top:196px"></i>
  </div>
  <div class="group" style="${abs(16, 690, 358, 104)}">
    <div class="grow"><span class="co c1x" style="flex:1">Sleep timer</span><span class="co c2x">Off</span><span class="c3x">${icon('chevron_right', 20)}</span></div><i class="sep" style="left:16px;top:52px"></i>
    <div class="grow" style="top:52px"><span class="co c1x" style="flex:1">Spatial audio</span><span class="co c2x">System</span><span class="c3x">${icon('chevron_right', 20)}</span></div>
  </div>
  ${minimizedChrome('library')}${gesture()}`;

S.replay = () => `${statusBar()}
  ${largeTitle('Replay')}
  <div class="segmented" style="${abs(16, 108, 358, 36)}"><span>Week</span><span class="on">Month</span><span>Year</span></div>
  <div class="stat num c1x" style="${abs(16, 168)}">1,284</div>
  <div class="co c2x" style="${abs(16, 238)}">minutes listened in October</div>
  <div class="fn c3x" style="${abs(16, 262)}">412 songs · 96 artists · 23 new to you</div>
  ${header(16, 312, 'Top Artists', false)}
  ${[['Field Theory', '214 min'], ['Mira Sol', '168 min'], ['The Quiet Hours', '131 min'], ['Kofi Mensah', '97 min']].map((a, i) =>
    `<div class="row" style="${abs(16, 352 + i * 56, 358, 56)}"><div class="rank num c2x" style="position:static;width:24px">${i + 1}</div>${avatar(a[0], 40)}<div class="row-text"><div class="rt c1x">${a[0]}</div></div><div class="fn c3x num">${a[1]}</div></div>`).join('')}
  ${header(16, 592, 'Top Songs', false)}
  ${[['lowtide', 'Low Tide', 'Field Theory', '38 plays'], ['lanterns', 'Paper Lanterns', 'Mira Sol', '31 plays'], ['nightbus', 'Night Bus Home', 'The Quiet Hours', '27 plays']].map((s, i) =>
    `<div class="rank num c2x" style="${abs(16, 632 + i * 60 + 19)}">${i + 1}</div>` + songRow(632 + i * 60, { id: s[0], title: s[1], sub: s[2], x: 40, w: 334, trailing: `<div class="fn c3x num">${s[3]}</div>` })).join('')}
  ${miniPlayer()}${tabBar('home')}${gesture()}`;

const skel = (x, y, w, h, r = 6) => `<i class="skel" style="${abs(x, y, w, h)}border-radius:${r}px"></i>`;
S.loading = () => `${statusBar()}
  ${largeTitle('Home', glassBtn(334, 52, 'account'))}
  ${skel(16, 112, 190, 22)}${skel(16, 150, 120, 14)}${skel(16, 172, 272, 356, 14)}${skel(300, 172, 272, 356, 14)}
  ${skel(16, 564, 160, 22)}${[16, 180, 344].map((x) => skel(x, 600, 152, 152, 10) + skel(x, 762, 120, 14) + skel(x, 782, 80, 12)).join('')}
  ${tabBar('home')}${gesture()}`;

const emptyState = (y, ic, title, body, actions) => `<div class="empty" style="${abs(16, y, 330)}"><div class="c2x">${icon(ic, 32)}</div>
  <div class="t2 c1x" style="margin-top:16px">${title}</div><div class="sh c2x" style="margin-top:8px">${body}</div><div class="pills" style="margin-top:20px">${actions}</div></div>`;
S.offline = () => `${statusBar()}
  ${largeTitle('Browse')}
  ${emptyState(120, 'cloud_off', 'You’re offline', 'Browse needs a connection. Your library, saved songs and anything you played recently still work.', pill('Go to Library', 'library', 'secondary'))}
  ${header(16, 372, 'Available Offline', false)}<div class="fn c3x" style="${abs(16, 402)}">Played recently, kept on this phone</div>
  ${card(16, 432, 'lanterns', 152, 'Paper Lanterns', 'Mira Sol')}${card(180, 432, 'latenight', 152, 'Late Night Drives', 'Playlist · Saved')}${card(344, 432, 'nightbus', 152, 'Night Bus Home', 'The Quiet Hours')}
  ${miniPlayer()}${tabBar('browse')}${gesture()}`;

S.empty = () => `${statusBar()}
  ${largeTitle('Library')}
  ${emptyState(120, 'song', 'Allow access to your music', 'AURA plays the audio files already on this phone. Nothing is uploaded, and you can change this in Settings.',
    pill('Allow Access', null, 'filled') + `<div class="textbtn sh acc">Browse the catalogue</div>`)}
  <div class="group" style="${abs(16, 392, 358, 84)}"><div class="grow tall" style="height:84px"><div class="acc">${icon('cloud_check', 24)}</div><div style="flex:1;margin-left:12px"><div class="co c1x">Save from Browse</div>
    <div class="fn c2x">Songs you save stream on demand and play offline once heard.</div></div></div></div>
  ${tabBar('library')}${gesture()}`;

S.searchError = () => `${statusBar()}
  <div class="chips" style="${abs(16, 52)}">${chip('All', true)}${chip('Songs')}${chip('Artists')}${chip('Albums')}${chip('Playlists')}</div>
  <div class="notice" style="${abs(16, 104, 358)}"><div class="c2x">${icon('alert', 22)}</div><div style="flex:1"><div class="sh w500 c1x">Couldn’t reach Audius</div>
    <div class="fn c2x">Showing your library only.</div></div><div class="sh acc w500">Try Again</div></div>
  ${header(16, 188, 'On This Phone', false)}
  ${songRow(228, { id: 'monsoon', title: 'Monsoon Letters', sub: 'Aanya Rao', src: 'phone' })}
  ${songRow(288, { id: 'monsoon', title: 'First Rain', sub: 'Aanya Rao · Monsoon Letters', src: 'phone' })}
  ${songRow(348, { id: 'smallhours', title: 'Monsoon Radio', sub: 'Theo Vance · Small Hours', src: 'phone' })}
  ${miniPlayer(704)}${searchBar('monsoon')}${gesture()}`;

S.sleep = () => `<div class="under soft">${S.nowPlaying()}</div><div class="scrim"></div>
  <div class="glass sheet-panel" style="${abs(8, 420, 374, 416)}"><div class="grabber" style="left:169px;top:8px"></div>
    <div class="hl c1x" style="position:absolute;left:24px;top:32px">Sleep Timer</div><div class="fn c2x" style="position:absolute;left:24px;top:56px">Playback pauses with a short fade.</div>
    ${[['Off', true], ['In 15 minutes'], ['In 30 minutes'], ['In 45 minutes'], ['In 1 hour'], ['When this song ends']].map((o, i) =>
      `<div class="srow" style="top:${92 + i * 52}px"><span class="co c1x">${o[0]}</span>${o[1] ? `<span class="acc">${icon('check', 22)}</span>` : ''}${i < 5 ? '<i class="sep" style="left:0"></i>' : ''}</div>`).join('')}
  </div>${gesture()}`;

const AZ = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ#'.split('');
S.songs = () => `${statusBar()}
  ${glassBtn(16, 48, 'back')}${glassBtn(334, 48, 'sort')}
  <div class="lt c1x" style="${abs(16, 100)}">Songs</div>
  <div class="fn c3x num" style="${abs(16, 142)}">1,248 songs · sorted by title</div>
  <div class="pills" style="${abs(16, 172, 358)}">${pill('Play', 'play', 'primary', 175)}${pill('Shuffle', 'shuffle', 'secondary', 175)}</div>
  ${[['afterimage', 'Afterimage', 'Nadia Karim', 'phone'], ['saltwater', 'All the Way Down', 'Ana Reyes', 'phone'], ['lowtide', 'Already Home', 'Field Theory', 'phone'],
     ['latenight', 'Arrivals', 'Tunde Ade · Saved', 'saved'], ['golden', 'August Static', 'Sunday Club', 'phone'], ['northern', 'Back Roads', 'Kofi Mensah', 'phone'],
     ['bluehour', 'Blue Hour', 'Lena Moreau', 'phone'], ['tidal', 'Breakwater', 'Juno Park · Saved', 'saved'], ['concrete', 'Brick & Moss', 'Grey Atlas', 'phone']]
    .map((r, i) => songRow(236 + i * 60, { id: r[0], title: r[1], sub: r[2], src: r[3] === 'saved' ? 'saved' : null, w: 336, state: i === 2 ? 'playing' : null })).join('')}
  <div class="az">${AZ.map((l) => `<span>${l}</span>`).join('')}</div>
  ${minimizedChrome('library')}${gesture()}`;

// ------------------------------------------------------------------ sheets (wide frames)
const swatches = (theme) => Object.entries(color[theme]).filter(([k]) => !k.startsWith('glass') && k !== 'scrim')
  .map(([k, v]) => `<div class="sw"><i style="background:${v}"></i><b>${k}</b><span>${v}</span></div>`).join('');
const TYPE_ROWS = [['lt', 'Large Title', '34 / 40 · Inter Display Bold'], ['t1', 'Title 1', '28 / 34 · Inter Display Bold'], ['t2', 'Title 2', '22 / 28 · Inter Display Bold'],
  ['t3', 'Title 3', '20 / 24 · Inter SemiBold'], ['hl', 'Headline', '17 / 22 · Inter SemiBold'], ['bd', 'Body', '17 / 22 · Inter Regular'], ['co', 'Callout', '16 / 21 · Inter Regular'],
  ['sh', 'Subhead', '15 / 20 · Inter Regular'], ['fn', 'Footnote', '13 / 18 · Inter Regular'], ['c1', 'Caption 1', '12 / 16 · Inter Medium'], ['c2', 'Caption 2', '11 / 13 · Inter Medium']];
function systemSheet() {
  return `<div class="sheet-grid">
  <div class="blk"><h3>Colour · dark</h3><div class="sws">${swatches('dark')}</div></div>
  <div class="blk light-blk"><h3>Colour · light</h3><div class="sws">${swatches('light')}</div></div>
  <div class="blk" style="grid-row:span 2"><h3>Type scale</h3>${TYPE_ROWS.map(([c, n, m]) => `<div class="ts"><span class="${c} c1x">${n}</span><span class="fn c3x">${m}</span></div>`).join('')}
    <div class="ts"><span class="co c1x num">1:24 · −2:48 · 1,284</span><span class="fn c3x">Tabular figures for every time and count</span></div></div>
  <div class="blk"><h3>Glass — navigation &amp; controls only</h3><div class="glass-demo">${art('golden', 300, 0)}<div class="glass capsule" style="position:absolute;left:24px;top:40px;width:252px;height:56px"></div>
    <div class="glass circle" style="position:absolute;left:24px;top:116px;width:56px;height:56px;display:grid;place-items:center">${icon('search', 24)}</div>
    <div class="glass" style="position:absolute;left:96px;top:116px;width:180px;height:120px;border-radius:22px"></div></div>
    <div class="fn c2x" style="margin-top:12px">Blur 24dp of the content behind + saturation 1.8 · tint #1C1C1E at 60% (light: white 65%) · 1dp rim, white 35% → 5% top to bottom · shadow y 8, blur 24, black 35%. API 26–30: tint at 92%, no blur.</div></div>
  <div class="blk"><h3>Buttons, chips, controls</h3><div class="pills">${pill('Play', 'play', 'primary', 150)}${pill('Shuffle', 'shuffle', 'secondary', 150)}</div>
    <div class="pills" style="margin-top:12px">${pill('Allow Access', null, 'filled')}${pill('Go to Library', 'library', 'secondary')}</div>
    <div class="chips" style="margin-top:16px">${chip('All', true)}${chip('Songs')}${chip('Artists')}${chip('Albums')}</div>
    <div class="segmented" style="margin-top:16px;position:relative;width:300px;height:36px"><span>Week</span><span class="on">Month</span><span>Year</span></div>
    <div style="display:flex;gap:16px;margin-top:16px;align-items:center">${toggle(true)}${toggle(false)}<div class="glass circle btn" style="position:relative;width:40px;height:40px">${icon('more', 22)}</div><span class="explicit">E</span><span class="c2x">${SRC.cloud}</span><span class="c2x">${SRC.saved}</span></div></div>
  <div class="blk" style="grid-column:span 2"><h3>Rows &amp; cards</h3><div style="position:relative;height:200px">
    ${songRow(0, { id: 'lowtide', title: 'Low Tide', sub: 'Field Theory', state: 'playing', x: 0 })}${songRow(60, { id: 'latenight', title: 'Nightswimming in Lagos', sub: 'Tunde Ade · Audius', src: 'cloud', x: 0, explicit: true })}
    ${songRow(120, { num: 3, title: 'Salt Air', sub: '', x: 0, trailing: `<div class="fn c3x num" style="margin-right:4px">3:05</div><div class="row-more c2x">${icon('more', 20)}</div>` })}
    ${card(390, 0, 'concrete', 120, 'Concrete Garden', 'Grey Atlas')}${card(526, 0, null, 120, 'Field Theory', 'Artist', { circle: true })}${card(662, 0, 'smallhours', 120, 'Small Hours', 'No artwork')}</div></div>
  <div class="blk" style="grid-column:span 3"><h3>Icons · 24dp grid, 1.8 stroke, round caps</h3><div class="icons">${Object.keys(ICONS).map((k) => `<div>${icon(k, 24)}<span>${k}</span></div>`).join('')}</div></div>
  </div>`;
}
function brandSheet() {
  const lockup = (ink) => `<div style="display:flex;align-items:center;gap:16px;color:${ink}">${mark(64)}<span style="font:600 44px/1 InterD;letter-spacing:.06em">AURA</span></div>`;
  return `<div class="brand">
    <div class="b-hero"><div style="color:#F5F5F7">${mark(220)}</div><div><div class="lt" style="color:#F5F5F7">Refraction</div>
      <div class="co" style="color:#A1A1A8;max-width:420px;margin-top:12px">A disc cut by a plane of glass. The upper half sits in air; the lower half is seen through the glass and appears displaced. Content stays sharp, the glass layer bends the light — the product in one shape.</div>
      <div class="fn" style="color:#8F8F96;margin-top:16px">Grid 48 · two semicircles r 18.5 · gap 2.6 · offset 5.6 · monochrome; colour comes from context.</div></div></div>
    <div class="b-row"><div class="b-tile" style="background:#0B0B0C">${lockup('#F5F5F7')}</div><div class="b-tile" style="background:#FFFFFF">${lockup('#111113')}</div></div>
    <div class="b-row"><div class="b-tile icons-tile">
      ${[120, 72, 48].map((s) => `<div class="appicon" style="width:${s}px;height:${s}px;border-radius:${s * 0.225}px">${mark(s * 0.62, '#F5F5F7')}</div>`).join('')}
      <div class="appicon" style="width:72px;height:72px;border-radius:50%;background:#D7DDE6;color:#2B3440">${mark(44)}</div>
      <div class="appicon" style="width:72px;height:72px;border-radius:50%;background:#3B4231;color:#DCE5C9">${mark(44)}</div>
      <div style="display:flex;gap:18px;align-items:end;color:#F5F5F7;margin-left:12px">${mark(24)}${mark(16)}</div>
      <div class="fn" style="color:#8F8F96;margin-left:auto;max-width:220px">Launcher icon (graphite, adaptive) · Android 13 themed icons tint the monochrome layer · in-UI minimum 16dp.</div></div></div>
  </div>`;
}

// ------------------------------------------------------------------ page
const FRAMES = [
  ['home', 'Home', S.home], ['home-scrolled', 'Home — scrolled: glass top bar, minimised tab bar, docked mini player', S.homeScrolled],
  ['browse', 'Browse', S.browse], ['search', 'Search', S.search], ['search-results', 'Search results — library + catalogue', S.searchResults],
  ['library', 'Library', S.library], ['album', 'Album', () => S.album()], ['playlist', 'Playlist (catalogue)', S.playlist], ['artist', 'Artist', S.artist],
  ['now-playing', 'Now Playing', S.nowPlaying], ['lyrics', 'Lyrics', S.lyrics], ['queue', 'Queue', S.queue], ['context-menu', 'Context menu', S.menu],
  ['sound', 'Sound — equaliser & playback', S.sound], ['replay', 'Replay', S.replay], ['loading', 'Loading', S.loading], ['offline', 'Offline', S.offline],
  ['empty', 'Empty library — first run', S.empty], ['search-error', 'Partial failure — catalogue unreachable', S.searchError],
  ['sleep-timer', 'Bottom sheet — sleep timer', S.sleep], ['songs', 'Library — Songs', S.songs],
  ['home-light', 'Home — light', S.home, 'light'], ['album-light', 'Album — light', () => S.album('light'), 'light'],
];

const CSS = `
@font-face{font-family:Inter;src:url(../app/src/main/res/font/inter_regular.otf) format('opentype');font-weight:400}
@font-face{font-family:Inter;src:url(../app/src/main/res/font/inter_medium.otf) format('opentype');font-weight:500}
@font-face{font-family:Inter;src:url(../app/src/main/res/font/inter_semibold.otf) format('opentype');font-weight:600}
@font-face{font-family:Inter;src:url(../app/src/main/res/font/inter_bold.otf) format('opentype');font-weight:700}
@font-face{font-family:InterD;src:url(../app/src/main/res/font/inter_display_bold.otf) format('opentype');font-weight:100 900}
:root,.dark{--canvas:${color.dark.canvas};--surface:${color.dark.surface};--elev:${color.dark.surfaceElevated};--fill:${color.dark.fill};--fill2:${color.dark.fillStrong};
 --sep:${color.dark.separator};--t1:${color.dark.textPrimary};--t2:${color.dark.textSecondary};--t3:${color.dark.textTertiary};--accent:${color.dark.accent};--on-accent:${color.dark.onAccent};
 --glass:${color.dark.glassTint};--rim1:${color.dark.glassRimTop};--rim2:${color.dark.glassRimBottom};--gshadow:${color.dark.glassShadow};--lens:rgba(255,255,255,.12);
 --scrim:${color.dark.scrim};--skel:${color.dark.skeleton};--skel2:${color.dark.skeletonHighlight};--toggle-off:#39393D}
.light{--canvas:${color.light.canvas};--surface:${color.light.surface};--elev:${color.light.surfaceElevated};--fill:${color.light.fill};--fill2:${color.light.fillStrong};
 --sep:${color.light.separator};--t1:${color.light.textPrimary};--t2:${color.light.textSecondary};--t3:${color.light.textTertiary};--accent:${color.light.accent};--on-accent:${color.light.onAccent};
 --glass:${color.light.glassTint};--rim1:${color.light.glassRimTop};--rim2:${color.light.glassRimBottom};--gshadow:${color.light.glassShadow};--lens:rgba(0,0,0,.06);
 --scrim:${color.light.scrim};--skel:${color.light.skeleton};--skel2:${color.light.skeletonHighlight};--toggle-off:#E3E3E8}
*{box-sizing:border-box;margin:0;padding:0}
body{background:#050506;color:#F5F5F7;font-family:Inter,sans-serif;-webkit-font-smoothing:antialiased;padding:48px}
.page-head{display:flex;align-items:center;gap:16px;margin-bottom:8px}.page-head .lt{color:#F5F5F7}
.page-sub{color:#8F8F96;font:400 15px/20px Inter;margin-bottom:40px;max-width:760px}
.frames{display:flex;flex-wrap:wrap;gap:48px 40px}
.frame .cap{font:500 13px/18px Inter;color:#8F8F96;margin-bottom:12px;max-width:390px}
.phone{width:390px;height:844px;position:relative;overflow:hidden;background:var(--canvas);color:var(--t1);border-radius:28px;isolation:isolate}
.phone>*,.phone .abs{position:absolute}
.lt{font:700 34px/40px InterD;letter-spacing:-.012em}.t1{font:700 28px/34px InterD;letter-spacing:-.01em}.t2{font:700 22px/28px InterD;letter-spacing:-.008em}
.t3{font:600 20px/24px Inter;letter-spacing:-.016em}.hl{font:600 17px/22px Inter;letter-spacing:-.013em}.bd{font:400 17px/22px Inter;letter-spacing:-.013em}
.co{font:400 16px/21px Inter;letter-spacing:-.011em}.sh{font:400 15px/20px Inter;letter-spacing:-.009em}.fn{font:400 13px/18px Inter;letter-spacing:-.003em}
.c1{font:500 12px/16px Inter}.c2{font:500 11px/13px Inter;letter-spacing:.005em}.caps{text-transform:uppercase;letter-spacing:.06em}
.w400{font-weight:400}.w500{font-weight:500}.num{font-variant-numeric:tabular-nums}
.c1x{color:var(--t1)}.c2x{color:var(--t2)}.c3x{color:var(--t3)}.acc{color:var(--accent)}
.ell{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.ell2{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.ic{display:block;flex:none}
.status{left:0;top:0;width:390px;height:40px;display:flex;align-items:center;justify-content:space-between;padding:4px 28px 0 32px;font:600 15px/1 Inter;color:var(--t1);z-index:5}
.status .sys{display:flex;gap:6px;align-items:center}.status svg{fill:currentColor}.status.on-art{color:#F5F5F7}
.gesture{left:141px;bottom:8px;width:108px;height:4px;border-radius:2px;background:var(--t1);opacity:.55;z-index:9}
.art{position:relative;overflow:hidden;flex:none;container-type:size}.art>*{position:absolute;font-style:normal}
.art.smart{background:var(--fill);display:grid;place-items:center;color:var(--t2)}
.glass{position:absolute;background:var(--glass);-webkit-backdrop-filter:blur(24px) saturate(180%);backdrop-filter:blur(24px) saturate(180%);box-shadow:0 8px 24px var(--gshadow);z-index:6}
.glass::before{content:"";position:absolute;inset:0;border-radius:inherit;padding:1px;background:linear-gradient(180deg,var(--rim1),var(--rim2));
 -webkit-mask:linear-gradient(#000 0 0) content-box,linear-gradient(#000 0 0);-webkit-mask-composite:xor;mask-composite:exclude;pointer-events:none}
.capsule{border-radius:999px}.circle{border-radius:50%}.btn{display:grid;place-items:center;color:var(--t1)}
.glass.bar{border-radius:0;box-shadow:none;z-index:4}.glass.bar::before{background:none;border-bottom:1px solid var(--sep);padding:0}
.tabbar{display:flex;align-items:center;padding:4px}.tab{flex:1;height:56px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;position:relative;color:var(--t1)}
.tab span{font:500 10px/12px Inter;letter-spacing:.01em}.tab.on{color:var(--accent)}.tab .lens{position:absolute;inset:0;border-radius:999px;background:var(--lens)}
.tab .ic,.tab span{position:relative}.search.on{color:var(--accent)}
.mini{display:flex;align-items:center;padding:0 4px 0 8px;gap:12px}.mini .art{border-radius:8px}.mini-text{flex:1;min-width:0}.mini-btn{width:44px;height:44px;display:grid;place-items:center;color:var(--t1)}
.mini.small{gap:10px;padding-left:8px}.tabmin{color:var(--accent)}
.field{display:flex;align-items:center;gap:10px;padding:0 16px;color:var(--t2)}.field .clear{margin-left:auto;width:20px;height:20px;border-radius:50%;background:var(--fill2);display:grid;place-items:center;color:var(--t2)}
.sec{display:flex;align-items:center;gap:2px;height:28px}.sec .chev{color:var(--t3);margin-top:1px}
.card{position:absolute}.feature{border-radius:14px;overflow:hidden}.feature-foot{height:84px;padding:12px 14px}
.row{display:flex;align-items:center;gap:12px}.row-text{flex:1;min-width:0}.rt{font:500 16px/21px Inter;letter-spacing:-.011em}
.row .sub{display:flex;align-items:center;gap:4px}.row .src{opacity:.9}.row-more{width:32px;height:44px;display:grid;place-items:center;margin-right:-6px}
.row.dim{opacity:.55}.num-col{width:24px;text-align:center;font:400 15px/20px Inter;font-variant-numeric:tabular-nums}
.explicit{display:inline-grid;place-items:center;width:14px;height:14px;border-radius:3px;background:var(--t3);color:var(--canvas);font:700 9px/1 Inter;flex:none}
.art-eq{position:absolute;inset:0;border-radius:6px;background:rgba(0,0,0,.45);display:grid;place-items:center}
.eqbars{display:inline-flex;gap:2px;align-items:flex-end;height:14px}.eqbars i{width:3px;border-radius:1.5px;background:currentColor;display:block}
.rank{position:absolute;width:20px;font:500 15px/20px Inter;text-align:left}
.lrow{display:flex;align-items:center;gap:16px}.lrow .sep{position:absolute;right:0;bottom:0;height:1px;background:var(--sep)}
.sep{position:absolute;right:0;height:1px;background:var(--sep);display:block}
.pills{display:flex;gap:8px;align-items:center}.pill{height:48px;border-radius:999px;display:flex;align-items:center;justify-content:center;gap:8px;padding:0 20px;font:600 16px/1 Inter;letter-spacing:-.01em}
.pill.primary{background:var(--fill);color:var(--accent)}.pill.secondary{background:var(--fill);color:var(--t1)}.pill.filled{background:var(--accent);color:var(--on-accent)}
.iconpill{width:48px;height:48px;border-radius:50%;background:var(--fill);display:grid;place-items:center;color:var(--t1)}
.textbtn{padding:0 12px;height:48px;display:flex;align-items:center}
.chips{display:flex;gap:8px}.chip{height:32px;padding:0 14px;border-radius:999px;background:var(--fill);color:var(--t1);font:500 14px/32px Inter;white-space:nowrap}
.chip.on{background:var(--t1);color:var(--canvas)}
.genre{border-radius:10px;padding:12px;color:#F5F5F7;position:absolute;overflow:hidden}
.qicon{width:44px;height:44px;border-radius:50%;background:var(--fill);display:grid;place-items:center}
.row.top .art{border-radius:10px}.playcirc{width:40px;height:40px;border-radius:50%;background:var(--fill);display:grid;place-items:center;color:var(--accent)}
.artist-hero{background:#163836;overflow:hidden}.artist-hero b{position:absolute;left:-12px;top:22px;font:700 300px/1 InterD;letter-spacing:-.04em;color:#1B4240}
.top-scrim{left:0;top:0;width:390px;height:112px;background:linear-gradient(180deg,rgba(0,0,0,.38),rgba(0,0,0,0));z-index:4}
.mosaic{width:152px;height:152px;border-radius:10px;overflow:hidden;display:grid;grid-template-columns:76px 76px}
.hero-scrim{position:absolute;left:0;right:0;bottom:0;height:180px;background:linear-gradient(180deg,rgba(11,11,12,0),rgba(11,11,12,.85))}
.playfab{border-radius:50%;background:var(--accent);color:var(--on-accent);display:grid;place-items:center;z-index:3}
.mesh{left:0;top:0;width:390px;height:844px;background:var(--m4);overflow:hidden}
.mesh i{position:absolute;border-radius:50%;filter:blur(70px)}
.mesh i:nth-child(1){left:-120px;top:-100px;width:460px;height:460px;background:var(--m1);opacity:.95}
.mesh i:nth-child(2){right:-160px;top:300px;width:420px;height:420px;background:var(--m3);opacity:.35}
.mesh i:nth-child(3){left:-80px;bottom:-140px;width:480px;height:480px;background:var(--m2)}
.mesh i:nth-child(4){left:80px;top:260px;width:300px;height:300px;background:var(--m1);opacity:.4}
.mesh::after{content:"";position:absolute;inset:0;background:rgba(0,0,0,.22)}
.grabber{position:absolute;left:177px;top:46px;width:36px;height:5px;border-radius:3px;background:rgba(255,255,255,.4);z-index:3}
.np-art .art{box-shadow:0 24px 48px rgba(0,0,0,.35),0 4px 12px rgba(0,0,0,.2)}
.scrub .track{height:4px;border-radius:2px;background:rgba(255,255,255,.22);position:relative}.scrub .track i{position:absolute;left:0;top:0;bottom:0;border-radius:2px;background:rgba(255,255,255,.88)}
.scrub .times{display:flex;justify-content:space-between;margin-top:10px;font:500 12px/16px Inter;color:rgba(245,245,247,.64)}
.mini-scrub .track{background:var(--fill2)}.mini-scrub .track b{position:absolute;top:-10px;width:24px;height:24px;margin-left:-12px;border-radius:50%;background:#FFFFFF;box-shadow:0 2px 6px rgba(0,0,0,.25)}
.transport{display:flex;justify-content:space-around;align-items:center;color:#F5F5F7;height:72px}
.volume{display:flex;align-items:center;gap:14px;color:rgba(245,245,247,.64)}.volume .track{flex:1;height:4px;border-radius:2px;background:rgba(255,255,255,.22);position:relative}.volume .track i{position:absolute;left:0;top:0;bottom:0;border-radius:2px;background:rgba(255,255,255,.75)}
.util{display:flex;align-items:center;justify-content:space-between;padding:0 8px;--glass:rgba(255,255,255,.14);--rim1:rgba(255,255,255,.4);--rim2:rgba(255,255,255,.08);--gshadow:rgba(0,0,0,.18)}
.ub{width:52px;height:40px;border-radius:999px;display:grid;place-items:center;color:#F5F5F7}.ub.on{background:#F5F5F7;color:#151517}
.np-head{display:flex;align-items:center;gap:12px}.np-head .art{border-radius:6px}
.lyrics p{font:700 30px/37px InterD;letter-spacing:-.012em;color:rgba(255,255,255,.36);margin-bottom:18px}
.lyrics p.on{color:#FFFFFF}.lyrics p.past{color:rgba(255,255,255,.26);filter:blur(1.2px)}.lyrics p.next{filter:blur(.6px)}
.on-art-2{color:rgba(245,245,247,.64)}.on-art-3{color:rgba(245,245,247,.45)}
.phone .row .rt:not(.c1x):not(.acc){color:#F5F5F7}
.qtoggles{display:flex;gap:8px}.qtoggles span{width:44px;height:36px;border-radius:999px;display:grid;place-items:center;color:#F5F5F7;background:rgba(255,255,255,.12)}
.qtoggles span.on{background:#F5F5F7;color:#151517}
.lifted{border-radius:14px;background:rgba(255,255,255,.16);box-shadow:0 12px 32px rgba(0,0,0,.3);z-index:3}
.lifted.solid{background:var(--elev);z-index:8}.lifted .row{position:absolute}
.under{inset:0;filter:blur(18px) saturate(1.2);transform:scale(1.02)}.under.soft{filter:blur(6px)}
.sheet-panel{border-radius:32px;--glass:rgba(28,28,30,.72);z-index:9}.srow{position:absolute;left:24px;right:24px;height:52px;display:flex;align-items:center;justify-content:space-between}.srow .sep{bottom:0}
.az{position:absolute;right:4px;top:232px;width:16px;display:flex;flex-direction:column;align-items:center;gap:1px;font:600 10px/18px Inter;color:var(--t2)}.under>*{position:absolute}
.scrim{inset:0;background:var(--scrim);z-index:7}
.menu{border-radius:22px;padding:8px;z-index:9}.mi{height:46px;display:flex;align-items:center;justify-content:space-between;padding:0 12px;border-radius:14px;font:400 17px/22px Inter;letter-spacing:-.013em;color:var(--t1)}
.mi:first-child{background:var(--lens)}.msep{height:8px;margin:4px -8px;background:rgba(0,0,0,.18)}
.group{border-radius:14px;background:var(--surface)}.grow{position:absolute;left:16px;right:16px;top:0;height:52px;display:flex;align-items:center;gap:8px}.grow.tall{height:64px}
.toggle{width:51px;height:31px;border-radius:999px;background:var(--toggle-off);position:relative;flex:none}.toggle i{position:absolute;left:2px;top:2px;width:27px;height:27px;border-radius:50%;background:#FFFFFF;box-shadow:0 2px 4px rgba(0,0,0,.2)}
.toggle.on{background:var(--accent)}.toggle.on i{left:22px}
.bands{display:flex;justify-content:space-between}.svgcap{font:500 11px Inter;fill:var(--t3)}
.segmented{background:var(--fill);border-radius:10px;display:flex;padding:2px}.segmented span{flex:1;display:grid;place-items:center;font:500 13px/1 Inter;color:var(--t1);border-radius:8px}
.segmented span.on{background:var(--fill2);box-shadow:0 1px 3px rgba(0,0,0,.25);font-weight:600}
.light .segmented span.on{background:#FFFFFF;box-shadow:0 1px 3px rgba(0,0,0,.12)}
.stat{font:700 64px/68px InterD;letter-spacing:-.02em}
.skel{background:linear-gradient(100deg,var(--skel) 30%,var(--skel2) 50%,var(--skel) 70%);background-size:300% 100%;background-position:40% 0;display:block}
.empty{position:absolute}
.notice{display:flex;gap:12px;align-items:center;padding:14px 16px;border-radius:14px;background:var(--surface)}
.sheet{width:1180px;background:#0B0B0C;border-radius:28px;padding:40px;position:relative;color:#F5F5F7}
.sheet h3{font:600 13px/18px Inter;color:#8F8F96;text-transform:uppercase;letter-spacing:.06em;margin-bottom:16px}
.sheet-grid{display:grid;grid-template-columns:1fr 1fr 1fr;gap:40px}.blk{position:relative}
.sws{display:grid;grid-template-columns:1fr 1fr;gap:10px 12px}.sw{display:grid;grid-template-columns:28px 1fr;grid-template-rows:auto auto;column-gap:10px;align-items:center}
.sw i{grid-row:span 2;width:28px;height:28px;border-radius:8px;border:1px solid rgba(255,255,255,.12)}.sw b{font:500 12px/16px Inter;color:#F5F5F7}.sw span{font:400 11px/13px Inter;color:#8F8F96}
.light-blk .sw i{border-color:rgba(0,0,0,.1)}
.ts{display:flex;align-items:baseline;justify-content:space-between;gap:16px;padding:6px 0;border-bottom:1px solid rgba(255,255,255,.06)}
.glass-demo{position:relative;width:300px;height:260px;border-radius:14px;overflow:hidden}
.icons{display:grid;grid-template-columns:repeat(10,1fr);gap:18px 8px;color:#F5F5F7}.icons div{display:flex;flex-direction:column;align-items:center;gap:6px}.icons span{font:400 10px/12px Inter;color:#8F8F96}
.brand{width:1180px;background:#0B0B0C;border-radius:28px;padding:48px;display:flex;flex-direction:column;gap:24px}
.b-hero{display:flex;gap:56px;align-items:center;padding:24px}.b-row{display:flex;gap:24px}.b-tile{flex:1;height:220px;border-radius:20px;display:flex;align-items:center;justify-content:center;border:1px solid rgba(255,255,255,.08)}
.icons-tile{justify-content:flex-start;gap:24px;padding:0 40px;background:#161618;height:200px}.appicon{background:#151517;display:grid;place-items:center;box-shadow:0 0 0 1px rgba(255,255,255,.08)}
`;

export function buildPrototype() {
  const frames = FRAMES.map(([id, cap, fn, theme]) =>
    `<section class="frame"><div class="cap">${cap}</div><div class="phone ${theme || 'dark'}" id="${id}">${fn()}</div></section>`).join('\n');
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>AURA 2.0 — prototype</title>
<!-- Generated by design/src/build.mjs from design/src/prototype.mjs. Placeholder content only:
     invented artists and titles, typographic artwork, original lyrics. -->
<style>${CSS}</style></head><body>
<div class="page-head"><span style="color:#F5F5F7">${mark(36)}</span><span class="lt">AURA 2.0</span></div>
<div class="page-sub">Phone frames at 390 × 844 dp. Content scrolls full-bleed; the glass layer holds only navigation and controls. Colour comes from artwork; Ember marks state. Fonts are the Inter / Inter Display files bundled with the app. All artists, titles and lyrics are invented placeholders.</div>
<div class="frames">
${frames}
<section class="frame"><div class="cap">Design system</div><div class="sheet" id="system">${systemSheet()}</div></section>
<section class="frame"><div class="cap">Brand</div><div id="brand">${brandSheet()}</div></section>
</div></body></html>
`;
}
