// The AURA mark: "Refraction".
//
// A single disc cut by a plane of glass. The upper half sits in air; the lower half is seen
// through the glass, so it appears displaced to the right. It is the product in one shape:
// content stays sharp, and the glass layer above it bends the light.
//
// Geometry lives on a 48-unit grid: two semicircles of radius R, separated by GAP and offset
// horizontally by SHIFT. The mark is monochrome first; colour is applied by the context.

import fs from 'node:fs';

export const R = 18.5, GAP = 2.6, SHIFT = 5.6, GRID = 48;
const n = (v) => +v.toFixed(3);

/** Path data of the two halves, scaled by k and centred at (cx, cy). */
export function markPaths(k = 1, cx = 24, cy = 24) {
  const r = R * k, g = (GAP / 2) * k, s = (SHIFT / 2) * k;
  const top = `M${n(cx - s - r)},${n(cy - g)}A${n(r)},${n(r)} 0 0,1 ${n(cx - s + r)},${n(cy - g)}Z`;
  const bottom = `M${n(cx + s - r)},${n(cy + g)}A${n(r)},${n(r)} 0 0,0 ${n(cx + s + r)},${n(cy + g)}Z`;
  return [top, bottom];
}
/** Bounding box of the mark at scale k: width, height. */
export const markSize = (k = 1) => ({ w: (2 * R + SHIFT) * k, h: (2 * R + GAP) * k });

/** Wordmark outlines (Inter Display SemiBold, +6% tracking), extracted with fontTools. */
export function wordmark() {
  return JSON.parse(fs.readFileSync(new URL('./wordmark.json', import.meta.url)));
}

export function markSvg(fill = 'currentColor', k = 1) {
  return markPaths(k).map((d) => `<path d="${d}" fill="${fill}"/>`).join('');
}
