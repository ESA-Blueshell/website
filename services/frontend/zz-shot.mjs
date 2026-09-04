import {chromium} from "@playwright/test"

const [port, outDir] = process.argv.slice(2)
const pages = [
  ["board", "/board"],
  ["esports", "/esports/competitive-scene"],
  ["game", "/esports/valorant"],
]
const views = [
  ["desktop", 1440, 900],
  ["mobile", 390, 844],
]

const browser = await chromium.launch()
for (const [vname, w, h] of views) {
  for (const theme of ["light", "dark"]) {
    const ctx = await browser.newContext({viewport: {width: w, height: h}, deviceScaleFactor: 1})
    await ctx.addInitScript(t => {
      localStorage.setItem("esa-blueshell.nl:darkMode", String(t === "dark"))
    }, theme)
    const page = await ctx.newPage()
    for (const [name, path] of pages) {
      await page.goto(`http://localhost:${port}${path}`, {waitUntil: "networkidle"})
      // The header is the band under test; let its fonts and the blob settle.
      await page.waitForTimeout(1200)
      await page.screenshot({path: `${outDir}/${name}-${vname}-${theme}.png`, fullPage: false})
    }
    await ctx.close()
  }
}
await browser.close()
console.log("done", port)
