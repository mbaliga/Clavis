// Boot smoke test for the rebuilt web app (Playwright / Chromium).
//   node web/smoke.js build/clavis-standalone.html
// Asserts: no uncaught JS errors, onboarding renders, corpus decoded (D.scenes / D.lex counts).
// Writes smoke-result.json and boot.png next to the tested file. Exit 0 = pass.
// Environment: NODE_PATH must reach the `playwright` package; PLAYWRIGHT_BROWSERS_PATH the Chromium download
// (run.py sets both from /opt defaults when unset); CHROME_PATH may point at an explicit binary.
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const FILE = path.resolve(process.argv[2] || path.join(__dirname, '..', 'build', 'clavis-standalone.html'));
const DIR = path.dirname(FILE);
const OUT = path.join(DIR, 'boot.png');

(async () => {
  const opts = { headless: true };
  if (process.env.CHROME_PATH) opts.executablePath = process.env.CHROME_PATH;
  const browser = await chromium.launch(opts);
  const page = await browser.newPage({ viewport: { width: 430, height: 900 } });
  const pageErrors = [], consoleErrors = [], consoleWarn = [], failedRequests = [];
  page.on('pageerror', e => pageErrors.push(String(e && e.stack || e)));
  page.on('requestfailed', r => failedRequests.push({ url: r.url(), type: r.resourceType(), error: (r.failure() || {}).errorText }));
  page.on('console', m => {
    if (m.type() === 'error') {
      const loc = m.location() || {};
      consoleErrors.push({ text: m.text(), url: loc.url || null });
    } else if (m.type() === 'warning') consoleWarn.push(m.text());
  });
  const t0 = Date.now();
  await page.goto('file://' + FILE, { waitUntil: 'load', timeout: 120000 });
  const tLoad = Date.now() - t0;
  const onboardSel = '#view .ob h2';
  let bootOk = true, bootErr = null;
  try { await page.waitForSelector(onboardSel, { timeout: 60000 }); }
  catch (e) { bootOk = false; bootErr = String(e.message).split('\n')[0]; }
  const tBoot = Date.now() - t0;
  const info = await page.evaluate(() => {
    const out = {};
    try {
      out.hasD = typeof D !== 'undefined' && !!D;
      if (out.hasD) {
        out.scenes = D.scenes.length;
        out.lex = Object.fromEntries(Object.entries(D.lex).map(([l, d]) => [l, Object.keys(d).length]));
        out.etym = Object.fromEntries(Object.entries(D.etym).map(([l, d]) => [l, Object.keys(d).length]));
        out.pseudo = Object.fromEntries(Object.entries(D.pseudo).map(([l, d]) => [l, d.length]));
        out.pages = new Set(D.scenes.map(s => s.m)).size;
        out.idsSequential = D.scenes.every((s, i) => s.i === i);
      }
      out.images = typeof IMAGES !== 'undefined' ? Object.keys(IMAGES).length : null;
      out.sizes = typeof SIZES !== 'undefined' ? Object.keys(SIZES).length : null;
      out.g6 = typeof G6 !== 'undefined' ? (G6.version || 'present') : null;
      out.viewname = (document.querySelector('#viewname') || {}).textContent || null;
      const h2 = document.querySelector('#view .ob h2');
      out.onboardH2 = h2 ? h2.textContent : null;
      out.obButtons = Array.from(document.querySelectorAll('#view .ob button')).map(b => b.textContent);
      out.title = document.title;
    } catch (e) { out.evalError = String(e); }
    return out;
  });
  await page.screenshot({ path: OUT, fullPage: false });
  await browser.close();
  const netErrors = consoleErrors.filter(e => /^Failed to load resource/.test(e.text));
  const jsConsoleErrors = consoleErrors.filter(e => !/^Failed to load resource/.test(e.text));
  const result = { file: FILE, load_ms: tLoad, boot_ms: tBoot, onboarding_rendered: bootOk, boot_error: bootErr,
    page_errors: pageErrors, js_console_errors: jsConsoleErrors, network_resource_failures: netErrors, failed_requests: failedRequests,
    console_warnings: consoleWarn.slice(0, 10), data: info,
    pass: bootOk && pageErrors.length === 0 && jsConsoleErrors.length === 0 && !!info.hasD && info.scenes > 0
      && info.idsSequential === true && info.images === info.pages && info.onboardH2 === 'Nine languages. One graph.' };
  fs.writeFileSync(path.join(DIR, 'smoke-result.json'), JSON.stringify(result, null, 1));
  console.log(JSON.stringify(result, null, 1));
  process.exit(result.pass ? 0 : 1);
})().catch(e => { console.error('SMOKE FAILED', e); process.exit(2); });
