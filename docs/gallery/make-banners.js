// Renders the pictures made from social.html: the GitHub social preview (1280x640) and the README banner.
// Run from the repo root: NODE_PATH=$(npm root -g) node docs/gallery/make-banners.js
const { chromium } = require("playwright");
const path = require("path");
const { pathToFileURL } = require("url");

const root = path.resolve(__dirname, "../..");
const social = pathToFileURL(path.join(__dirname, "social.html")).href;
const pictures = [
  { file: "docs/images/social-preview.png", h: 640 },
  { file: "docs/images/banner.png", h: 400 },
];

(async () => {
  const browser = await chromium.launch();
  for (const p of pictures) {
    const tab = await browser.newPage({ viewport: { width: 1280, height: p.h } });
    await tab.goto(social + "?h=" + p.h);
    await tab.evaluate(async () => {
      await document.fonts.ready;
      await Promise.all([...document.images].map(img => img.decode()));
      // The canvas draws once its font is there
      await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
    });
    await tab.screenshot({ path: path.join(root, p.file) });
    console.log(p.file);
    await tab.close();
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
