// Renders an SVG file to PNG with Chromium: node render.js in.svg out.png width height
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const [input, output, width, height] = process.argv.slice(2);
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: +width, height: +height } });
  const svg = fs.readFileSync(input, 'utf8')
    .replace(/width="\d+" height="\d+"/, `width="${width}" height="${height}"`);
  await page.setContent(`<html><body style="margin:0">${svg}</body></html>`);
  await page.screenshot({ path: output });
  await browser.close();
})();
