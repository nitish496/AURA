// AURA icon family — the single source of truth.
//
// Every icon is drawn on a 24 x 24 grid with a 2-unit safe margin (live area 2..22).
// Outline icons use one stroke weight (STROKE) with round caps and joins; solid glyphs
// are reserved for transport controls and "selected" states. Filled variants of outline
// icons are drawn fill + stroke so their silhouette is identical to the outline version.
//
// `node design/src/build.mjs` turns this file into:
//   app/src/main/res/drawable/ic_<name>.xml          (Android VectorDrawable)
//   app/src/main/java/dev/lilt/player/ui/IconPaths.java (path table for GlyphDrawable)
//   the inline SVG icons used by design/prototype.html
//
// Layer modes: 's' stroke, 'f' fill, 'fe' fill even-odd, 'fs' fill + stroke.

export const STROKE = 1.8;

const n = (v) => +v.toFixed(3);
export const circle = (cx, cy, r) =>
  `M${n(cx - r)},${n(cy)}A${n(r)},${n(r)} 0 1,0 ${n(cx + r)},${n(cy)}A${n(r)},${n(r)} 0 1,0 ${n(cx - r)},${n(cy)}Z`;
export const rrect = (x, y, w, h, r) => {
  r = Math.min(r, w / 2, h / 2);
  return `M${n(x + r)},${n(y)}H${n(x + w - r)}A${n(r)},${n(r)} 0 0,1 ${n(x + w)},${n(y + r)}V${n(y + h - r)}` +
    `A${n(r)},${n(r)} 0 0,1 ${n(x + w - r)},${n(y + h)}H${n(x + r)}A${n(r)},${n(r)} 0 0,1 ${n(x)},${n(y + h - r)}` +
    `V${n(y + r)}A${n(r)},${n(r)} 0 0,1 ${n(x + r)},${n(y)}Z`;
};
// Polygon with softened corners: each corner is replaced by a quadratic curve that starts
// and ends `d` units along the adjacent edges.
export const roundedPoly = (pts, d) => {
  const len = pts.length, out = [];
  const along = (a, b, dist) => {
    const dx = b[0] - a[0], dy = b[1] - a[1], l = Math.hypot(dx, dy);
    return [a[0] + dx / l * dist, a[1] + dy / l * dist];
  };
  for (let i = 0; i < len; i++) {
    const p = pts[i], prev = pts[(i + len - 1) % len], next = pts[(i + 1) % len];
    const a = along(p, prev, d), b = along(p, next, d);
    out.push(i === 0 ? `M${n(a[0])},${n(a[1])}` : `L${n(a[0])},${n(a[1])}`);
    out.push(`Q${n(p[0])},${n(p[1])} ${n(b[0])},${n(b[1])}`);
  }
  return out.join('') + 'Z';
};
const gear = (cx, cy, outer, inner, teeth) => {
  const pts = [];
  const step = (Math.PI * 2) / teeth;
  for (let k = 0; k < teeth; k++) {
    const a = k * step - Math.PI / 2;
    const rootHalf = step * 0.34, tipHalf = step * 0.2;
    pts.push([cx + inner * Math.cos(a - rootHalf), cy + inner * Math.sin(a - rootHalf)]);
    pts.push([cx + outer * Math.cos(a - tipHalf), cy + outer * Math.sin(a - tipHalf)]);
    pts.push([cx + outer * Math.cos(a + tipHalf), cy + outer * Math.sin(a + tipHalf)]);
    pts.push([cx + inner * Math.cos(a + rootHalf), cy + inner * Math.sin(a + rootHalf)]);
  }
  return 'M' + pts.map((p) => `${n(p[0])},${n(p[1])}`).join('L') + 'Z';
};

const s = (d) => ({ d, mode: 's' });
const f = (d) => ({ d, mode: 'f' });
const fe = (d) => ({ d, mode: 'fe' });
const fs = (d) => ({ d, mode: 'fs' });

const HEART = 'M12,19.6C12,19.6 3.4,14.6 3.4,8.9C3.4,6.3 5.4,4.4 7.8,4.4C9.6,4.4 11.1,5.4 12,6.9' +
  'C12.9,5.4 14.4,4.4 16.2,4.4C18.6,4.4 20.6,6.3 20.6,8.9C20.6,14.6 12,19.6 12,19.6Z';
const HOME = 'M4.6,10.4L12,4.2L19.4,10.4V18.4C19.4,19.4 18.6,20.2 17.6,20.2H14.6V14.8' +
  'C14.6,14.2 14.2,13.8 13.6,13.8H10.4C9.8,13.8 9.4,14.2 9.4,14.8V20.2H6.4C5.4,20.2 4.6,19.4 4.6,18.4Z';
const CLOUD = 'M7.4,18.6H16.9C19.2,18.6 21,16.8 21,14.6C21,12.5 19.4,10.8 17.4,10.6' +
  'C16.9,7.8 14.6,5.8 11.8,5.8C9.3,5.8 7.2,7.4 6.5,9.6C4.5,10.1 3,11.8 3,14C3,16.5 5,18.6 7.4,18.6Z';
const REPEAT_LOOP = [
  s('M4.2,12V10.2C4.2,8.1 5.9,6.4 8,6.4H19.3'), s('M16.7,3.8L19.3,6.4L16.7,9'),
  s('M19.8,12V13.8C19.8,15.9 18.1,17.6 16,17.6H4.7'), s('M7.3,20.2L4.7,17.6L7.3,15'),
];
const BROWSE = [rrect(4, 4, 7, 7, 2.2), rrect(13, 4, 7, 7, 2.2), rrect(4, 13, 7, 7, 2.2), rrect(13, 13, 7, 7, 2.2)];
const LIBRARY_FRONT = rrect(3.8, 8.4, 16.4, 11.8, 2.6);
const SPEAKER = 'M3.6,9.6H6.8L11.4,5.6V18.4L6.8,14.4H3.6Z';

export const ICONS = {
  // Transport — solid glyphs, optically centred.
  play: [f(roundedPoly([[7.2, 4.4], [20.4, 12], [7.2, 19.6]], 2.0))],
  pause: [f(rrect(6.2, 4.8, 3.8, 14.4, 1.3)), f(rrect(14, 4.8, 3.8, 14.4, 1.3))],
  next: [f(roundedPoly([[2.8, 6.2], [12.2, 12], [2.8, 17.8]], 1.5)), f(roundedPoly([[11.8, 6.2], [21.2, 12], [11.8, 17.8]], 1.5))],
  previous: [f(roundedPoly([[21.2, 6.2], [11.8, 12], [21.2, 17.8]], 1.5)), f(roundedPoly([[12.2, 6.2], [2.8, 12], [12.2, 17.8]], 1.5))],
  shuffle: [
    s('M3,7.5H6.2C9.6,7.5 10.8,9.6 12,12C13.2,14.4 14.4,16.5 17.8,16.5H20.6'), s('M18.2,14.1L20.6,16.5L18.2,18.9'),
    s('M3,16.5H6.2C9.6,16.5 10.8,14.4 12,12C13.2,9.6 14.4,7.5 17.8,7.5H20.6'), s('M18.2,5.1L20.6,7.5L18.2,9.9'),
  ],
  repeat: REPEAT_LOOP,
  repeat_one: [...REPEAT_LOOP, s('M11,10.5L12.4,9.5V14.6')],
  autoplay: [s('M12,12C10.3,9.6 8.9,8.4 7,8.4C5,8.4 3.4,10 3.4,12C3.4,14 5,15.6 7,15.6C8.9,15.6 10.3,14.4 12,12' +
    'C13.7,9.6 15.1,8.4 17,8.4C19,8.4 20.6,10 20.6,12C20.6,14 19,15.6 17,15.6C15.1,15.6 13.7,14.4 12,12Z')],

  // Actions
  heart: [s(HEART)],
  heart_fill: [fs(HEART)],
  plus: [s('M12,5V19M5,12H19')],
  minus: [s('M5,12H19')],
  check: [s('M5,12.6L9.6,17.2L19,7.4')],
  close: [s('M6.6,6.6L17.4,17.4M17.4,6.6L6.6,17.4')],
  more: [f(circle(5.5, 12, 1.7)), f(circle(12, 12, 1.7)), f(circle(18.5, 12, 1.7))],
  more_vertical: [f(circle(12, 5.5, 1.7)), f(circle(12, 12, 1.7)), f(circle(12, 18.5, 1.7))],
  share: [s('M8.2,9.4H7.4C6.1,9.4 5,10.5 5,11.8V18.2C5,19.5 6.1,20.6 7.4,20.6H16.6C17.9,20.6 19,19.5 19,18.2V11.8C19,10.5 17.9,9.4 16.6,9.4H15.8'),
    s('M12,3.4V14.2'), s('M8.8,6.6L12,3.4L15.2,6.6')],
  play_next: [f(roundedPoly([[3.6, 4.6], [9.2, 8.1], [3.6, 11.6]], 0.9)), s('M12.4,8.1H20.4'), s('M3.6,15H20.4'), s('M3.6,19.4H20.4')],
  add_to_queue: [s('M3.6,4.6H20.4'), s('M3.6,9H20.4'), f(roundedPoly([[3.6, 12.4], [9.2, 15.9], [3.6, 19.4]], 0.9)), s('M12.4,15.9H20.4')],
  playlist_add: [s('M3.8,6.4H15'), s('M3.8,11H15'), s('M3.8,15.6H10.6'), s('M17.2,13V21M13.2,17H21.2')],
  edit: [s('M4.2,19.8L4.8,16.1L15.6,5.3C16.4,4.5 17.6,4.5 18.4,5.3L18.7,5.6C19.5,6.4 19.5,7.6 18.7,8.4L7.9,19.2Z'), s('M13.8,7.1L16.9,10.2')],
  trash: [s('M4.6,6.6H19.4'), s('M9.6,6.6V5C9.6,4.4 10,4 10.6,4H13.4C14,4 14.4,4.4 14.4,5V6.6'),
    s('M6.6,6.6L7.4,18.6C7.5,19.4 8.1,20 8.9,20H15.1C15.9,20 16.5,19.4 16.6,18.6L17.4,6.6'), s('M10.3,10.6V15.9'), s('M13.7,10.6V15.9')],
  drag: [s('M5,8.6H19'), s('M5,12H19'), s('M5,15.4H19')],
  sort: [s('M8,4.6V19.4'), s('M4.6,8L8,4.6L11.4,8'), s('M16,19.4V4.6'), s('M12.6,16L16,19.4L19.4,16')],
  filter: [s('M4,7H20'), s('M7,12H17'), s('M10,17H14')],
  refresh: [s('M19.4,13.2A7.6,7.6 0 1,1 17.2,6.9'), s('M17.8,3V7.2H13.6')],

  // Navigation
  chevron_right: [s('M9.4,5.4L16,12L9.4,18.6')],
  chevron_left: [s('M14.6,5.4L8,12L14.6,18.6')],
  chevron_down: [s('M5.4,9.2L12,15.8L18.6,9.2')],
  chevron_up: [s('M5.4,14.8L12,8.2L18.6,14.8')],
  back: [s('M14.6,5.4L8,12L14.6,18.6')],
  search: [s(circle(10.6, 10.6, 6.6)), s('M15.4,15.4L20.2,20.2')],
  home: [s(HOME)],
  home_fill: [fs(HOME)],
  browse: BROWSE.map(s),
  browse_fill: BROWSE.map(fs),
  library: [s(LIBRARY_FRONT), s('M6.4,5.2H17.6')],
  library_fill: [fs(LIBRARY_FRONT), s('M6.4,5.2H17.6')],
  account: [s(circle(12, 12, 8.8)), s(circle(12, 10, 3.1)), s('M6.6,18.3C7.9,16.5 9.8,15.4 12,15.4C14.2,15.4 16.1,16.5 17.4,18.3')],
  settings: [s(gear(12, 12, 9.2, 7.3, 8)), s(circle(12, 12, 2.9))],

  // Player utilities
  queue: [f(circle(5, 6.5, 1.3)), f(circle(5, 12, 1.3)), f(circle(5, 17.5, 1.3)), s('M9,6.5H20'), s('M9,12H20'), s('M9,17.5H20')],
  lyrics: [s('M6,4.6H18C19.4,4.6 20.4,5.6 20.4,7V14C20.4,15.4 19.4,16.4 18,16.4H11.2L7.4,19.6V16.4H6C4.6,16.4 3.6,15.4 3.6,14V7C3.6,5.6 4.6,4.6 6,4.6Z'),
    s('M8,9H16'), s('M8,12.2H13.2')],
  output: [s(rrect(6.2, 3, 11.6, 18, 3)), s(circle(12, 14.2, 2.9)), f(circle(12, 7.6, 1.1))],
  headphones: [s('M4,16V12.6C4,8.2 7.6,4.6 12,4.6C16.4,4.6 20,8.2 20,12.6V16'), s(rrect(3.6, 13.6, 4.2, 6.8, 1.6)), s(rrect(16.2, 13.6, 4.2, 6.8, 1.6))],
  bluetooth: [s('M7,7.6L16.6,16.2L12,20.2V3.8L16.6,7.8L7,16.4')],
  phone: [s(rrect(6.8, 2.6, 10.4, 18.8, 2.6)), s('M10.6,18.4H13.4')],
  timer: [s(circle(12, 13.2, 7.4)), s('M12,13.2V9.6'), s('M9.8,3.6H14.2')],
  moon: [s('M19.6,14.6C18.5,15.2 17.2,15.5 15.9,15.5C11.8,15.5 8.5,12.2 8.5,8.1C8.5,6.8 8.8,5.6 9.4,4.5' +
    'C6.2,5.5 3.9,8.5 3.9,12C3.9,16.4 7.5,20 11.9,20C15.4,20 18.5,17.8 19.6,14.6Z')],
  equaliser: [s('M6,4V11.4'), s('M6,15.6V20'), s(circle(6, 13.5, 2.1)), s('M12,4V5.9'), s('M12,10.1V20'), s(circle(12, 8, 2.1)),
    s('M18,4V12.9'), s('M18,17.1V20'), s(circle(18, 15, 2.1))],
  waveform: [s('M4,10.4V13.6'), s('M8,7.4V16.6'), s('M12,4.4V19.6'), s('M16,8.4V15.6'), s('M20,10.6V13.4')],
  volume_low: [s(SPEAKER), s('M15,9.4C15.8,10.1 16.3,11 16.3,12C16.3,13 15.8,13.9 15,14.6')],
  volume_high: [s(SPEAKER), s('M15,9.4C15.8,10.1 16.3,11 16.3,12C16.3,13 15.8,13.9 15,14.6'), s('M17.8,6.6C19.3,8 20.2,9.9 20.2,12C20.2,14.1 19.3,16 17.8,17.4')],

  // Library and metadata
  album: [s(circle(12, 12, 8.6)), s(circle(12, 12, 2.4))],
  artist: [s(circle(12, 8.2, 3.6)), s('M5,20C5.4,16.5 8.4,14.2 12,14.2C15.6,14.2 18.6,16.5 19,20')],
  song: [s('M9.1,17.2V6.6L19.1,4.4V15.2'), s(circle(6.8, 17.2, 2.3)), s(circle(16.8, 15.2, 2.3))],
  playlist: [s('M3.8,6.4H15.6'), s('M3.8,11H15.6'), s('M3.8,15.6H11'), s('M19.4,8.6V17.4'), s(circle(17.2, 17.4, 2.2))],
  clock: [s(circle(12, 12, 8.6)), s('M12,7.6V12L15,14')],
  trending: [s('M3.6,16.6L9,11.2L12.8,15L20.2,7.6'), s('M15.4,7.6H20.2V12.4')],
  stats: [s(rrect(4.2, 12.4, 3.6, 7.6, 1.2)), s(rrect(10.2, 7.4, 3.6, 12.6, 1.2)), s(rrect(16.2, 3.8, 3.6, 16.2, 1.2))],
  cloud: [s(CLOUD)],
  cloud_check: [s(CLOUD), s('M9.2,13.4L11.3,15.4L15,11.6')],
  cloud_off: [s(CLOUD), s('M3.6,3.6L20.4,20.4')],
  info: [s(circle(12, 12, 8.6)), s('M12,11V16.4'), f(circle(12, 7.9, 1.1))],
  alert: [s('M10.4,4.9C11.1,3.6 12.9,3.6 13.6,4.9L20.5,17.1C21.2,18.4 20.3,20 18.8,20H5.2C3.7,20 2.8,18.4 3.5,17.1Z'),
    s('M12,9.6V13.4'), f(circle(12, 16.6, 1.1))],
  explicit: [fe(rrect(3.6, 3.6, 16.8, 16.8, 3.4) + 'M9.4,7.8H14.8V9.6H11.5V11.1H14.3V12.9H11.5V14.4H14.8V16.2H9.4Z')],
};

// Legacy GlyphDrawable names used by the 1.x MainActivity.
export const ALIASES = { down: 'chevron_down', playlists: 'playlist', prev: 'previous' };
