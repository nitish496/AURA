// Renders design/prototype.html with Playwright.
//   node design/src/render.mjs review <outDir>   1x screenshots for design review
//   node design/src/render.mjs final             2x screenshots -> design/screens/*.png, docs/aura-logo.png
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
let playwright;
try { playwright = require('playwright'); } catch { playwright = require('/opt/node-tools/node_modules/playwright'); }

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../..');
const mode = process.argv[2] || 'review';
const out = mode === 'final' ? path.join(repo, 'design/screens') : path.resolve(process.argv[3] || 'review');
fs.mkdirSync(out, { recursive: true });

const browser = await playwright.chromium.launch();
const page = await browser.newPage({ viewport: { width: 1400, height: 1000 }, deviceScaleFactor: mode === 'final' ? 2 : 1 });
await page.goto('file://' + path.join(repo, 'design/prototype.html'));
await page.evaluate(() => document.fonts.ready);
await page.waitForTimeout(250);
const ids = await page.$$eval('.phone, #system, #brand', (els) => els.map((e) => e.id));
for (const id of ids) {
  await page.locator('#' + id).screenshot({ path: path.join(out, `${id}.png`), animations: 'disabled' });
}
if (mode === 'final') {
  const icon = await browser.newPage({ viewport: { width: 1024, height: 1024 }, deviceScaleFactor: 1 });
  await icon.setContent(`<html><body style="margin:0;background:transparent">${fs.readFileSync(path.join(repo, 'design/app-icon.svg'), 'utf8')}</body></html>`);
  await icon.locator('svg').screenshot({ path: path.join(repo, 'docs/aura-logo.png'), omitBackground: true });
}
await browser.close();
console.log(`rendered ${ids.length} frames to ${out}`);
