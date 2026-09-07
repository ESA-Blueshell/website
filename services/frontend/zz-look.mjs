// A fast look at /board: no build, no test runner. Against the dev server on 4173.
import {mkdirSync} from "node:fs"
import {fileURLToPath} from "node:url"
import {chromium} from "playwright"

// Repo-relative, so a shot never lands in somebody else's session directory.
const OUT = process.env.OUT || "./shots"
mkdirSync(OUT, {recursive: true})
const ROOT = fileURLToPath(new URL(".", import.meta.url))
const FACES = ["Emma", "Viktor", "Taha", "Sylwia", "Boris", "Rene"]
const ROLES = ["Chair", "Secretary", "Treasurer", "Commissioner of Internal Affairs",
  "Commissioner of External Affairs", "Commissioner of Esports Affairs"]
const BLURB = "Hi everyone, my name is here and this is the paragraph a board member wrote about "
  + "themselves when they took the seat. It runs a few lines, because people have things to say."

const pic = (dir, name, w, h) => ({
  path: `${dir}/${name}.webp`, url: `/files/public/${dir}/${name}.webp`, width: w, height: h,
  renditions: [160, 320, 640].map(width => ({url: `/files/public/${dir}/${name}-${width}.webp`, width})),
})

// blurbs: how many of the six wrote one. 6 = all, 0 = nobody (the four early boards)
const boards = (accent, blurbs = 6, photo = true, faces = true) => ([{
  id: 9, number: 9, name: "Eeveelutions", candidate: "Eeveelutions", cheer: "RNG, Be With Me!",
  accent,
  description: "The ninth board, who ran the association through the year and left it busier than they found it.",
  startDate: "2025-09-01", endDate: "2026-09-16", image: null,
  photo: photo ? pic("board-photos", "board9", 1300, 827) : null,
  version: 0, createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  members: FACES.map((face, i) => ({
    id: 90 + i, boardId: 9, userId: null, role: ROLES[i],
    name: `${face} Surname`, nickname: i % 2 === 0 ? `Nick${face}` : null,
    description: i < blurbs ? BLURB : null, image: null,
    portrait: faces ? pic("board-portraits", face, 640, 960) : null,
    startDate: "2025-09-01", endDate: "2026-09-16", version: 0,
    createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  })),
}])

// A line of boards, each with a colour of its own: what the strip is for once they have one.
const COLOURS = ["#65c6cd", "#b00b69", "#e8842a", "#9100d0", "#6cbf3f"]
const many = () => COLOURS.map((accent, i) => ({
  ...boards(accent)[0], id: 5 + i, number: 5 + i, name: `Board ${5 + i}`, accent,
  startDate: `${2021 + i}-09-01`, endDate: `${2022 + i}-09-16`,
}))

const scene = process.argv[2] || "all"
const browser = await chromium.launch()

async function shoot({name, accent, blurbs, photo, faces = true, light, hoverIndex, stops, width = 1440}) {
  const ctx = await browser.newContext({viewport: {width, height: 1100}, deviceScaleFactor: 1,
    colorScheme: light ? "light" : "dark"})
  const page = await ctx.newPage()
  await page.route("**/boards", r => r.fulfill({json: stops ? many() : boards(accent, blurbs, photo, faces)}))
  await page.route("**/files/public/board-photos/**", r => r.fulfill({path: `${ROOT}src/assets/board9/board9.jpg`}))
  for (const f of FACES) {
    await page.route(`**/files/public/board-portraits/${f}*`, r => r.fulfill({path: `${ROOT}src/assets/board9/${f}.jpg`}))
  }
  await page.goto("http://127.0.0.1:4173/board", {waitUntil: "networkidle"}).catch(() => {})
  await page.waitForTimeout(1500)
  // The cookie notice and the sticky nav sit over the page in a full-page shot.
  const gotIt = page.getByText("Got it", {exact: true})
  if (await gotIt.count()) await gotIt.first().click().catch(() => {})
  await page.addStyleTag({content: "header.v-app-bar,.v-app-bar{display:none!important}"}).catch(() => {})
  await page.waitForTimeout(400)
  // A stop on the strip rather than a slice in the band: the line lights in its colour.
  if (stops != null) {
    const node = page.getByTestId(`board-node-${stops}`)
    if (await node.count()) { await node.hover(); await page.waitForTimeout(700) }
  }
  if (hoverIndex != null) {
    const slice = page.locator("[data-testid^='board-member-9']").nth(hoverIndex)
    // Long enough for the pass to settle and the blurb to arrive after it, which is the state
    // worth looking at rather than the middle of the movement.
    if (await slice.count()) { await slice.hover(); await page.waitForTimeout(1600) }
  }
  if (process.env.DUMP) {
    console.log(JSON.stringify(await page.evaluate(() => {
      const slices = [...document.querySelectorAll("section.team-slice")]
      return slices.map(s => {
        const img = s.querySelector("img.team-slice__banner")
        const r = s.getBoundingClientRect()
        const b = img?.getBoundingClientRect()
        return {slice: [r.left | 0, r.width | 0], open: s.className.includes("--open"),
          img: img ? [b.left | 0, b.width | 0, b.height | 0, img.complete] : null,
          face: getComputedStyle(s).getPropertyValue("--face").trim(),
          w: img ? getComputedStyle(img).width : null, mask: img ? getComputedStyle(img).maskImage.slice(0, 60) : null}
      })
    }), null, 1))
  }
  await page.screenshot({path: `${OUT}/${name}.png`, fullPage: true})
  const band = page.getByTestId("board-band")
  if (await band.count()) await band.screenshot({path: `${OUT}/${name}-band.png`})
  console.log(`  ${name}.png`)
  await ctx.close()
}

const scenes = {
  main:    {name: "L1-main",      accent: "#9100d0", blurbs: 6, photo: true,  hoverIndex: 2},
  noblurb: {name: "L2-no-blurbs", accent: "#b00b69", blurbs: 0, photo: true,  hoverIndex: 2},
  nophoto: {name: "L3-no-photo",  accent: "#65c6cd", blurbs: 3, photo: false, hoverIndex: 1},
  light:   {name: "L4-pale-light",accent: "#eaa4b6", blurbs: 6, photo: true,  hoverIndex: 1, light: true},
  phone:   {name: "L5-phone",     accent: "#9100d0", blurbs: 6, photo: true,  width: 390},
  nopics:  {name: "L8-no-pictures", accent: "#65c6cd", blurbs: 3, photo: false, faces: false, hoverIndex: 1},
  nopicslight: {name: "L9-no-pictures-light", accent: "#65c6cd", blurbs: 3, photo: false, faces: false, hoverIndex: 1, light: true},
  line:    {name: "L6-line",      accent: "#9100d0", blurbs: 6, photo: true,  stops: 7},
  linelight: {name: "L7-line-light", accent: "#9100d0", blurbs: 6, photo: true, stops: 7, light: true},
}
for (const [k, s] of Object.entries(scenes)) {
  if (scene === "all" || scene === k) await shoot(s)
}
await browser.close()
