import AxeBuilder from "@axe-core/playwright"
import {expect, test, type Page} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"

/**
 * The Management pages built from the shared parts, checked by axe and by measured contrast.
 *
 * Axe cannot judge contrast on the island: the pattern behind every text run leaves its colour
 * rule "incomplete", which is no evidence either way. So contrast is measured instead: the text
 * colour against the median pixel behind each run, with the text hidden for the screenshot.
 */

type Rgb = [number, number, number]

const luminance = ([r, g, b]: Rgb) => {
  const linear = (channel: number) => {
    const scaled = channel / 255
    return scaled <= 0.04045 ? scaled / 12.92 : ((scaled + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
}

const contrast = (ink: Rgb, ground: Rgb) => {
  const [high, low] = [luminance(ink), luminance(ground)].sort((a, b) => b - a)
  return (high! + 0.05) / (low! + 0.05)
}

interface Run {
  text: string
  ink: Rgb
  large: boolean
  rect: {x: number; y: number; width: number; height: number}
}

/** Every visible text run in the page's main area, with its painted colour and its box. */
async function textRuns(page: Page): Promise<Run[]> {
  return page.evaluate(() => {
    const canvas = document.createElement("canvas")
    canvas.width = 1
    canvas.height = 1
    const ctx = canvas.getContext("2d", {willReadFrequently: true})!
    const paint = (colour: string): [number, number, number] => {
      ctx.clearRect(0, 0, 1, 1)
      ctx.fillStyle = colour
      ctx.fillRect(0, 0, 1, 1)
      const [r, g, b] = ctx.getImageData(0, 0, 1, 1).data
      return [r!, g!, b!]
    }
    const root = document.querySelector("main.mg-main") ?? document.body
    const runs: Run[] = []
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      const text = node.textContent?.trim() ?? ""
      const element = node.parentElement
      if (!text || !element) continue
      const style = getComputedStyle(element)
      if (style.visibility === "hidden" || style.display === "none" || Number(style.opacity) === 0) continue
      const range = document.createRange()
      range.selectNodeContents(node)
      const box = range.getBoundingClientRect()
      if (box.width < 4 || box.height < 4 || box.bottom < 0 || box.top > innerHeight || box.right < 0 || box.left > innerWidth) continue
      const size = parseFloat(style.fontSize)
      const bold = Number(style.fontWeight) >= 700
      runs.push({
        text: text.slice(0, 40),
        ink: paint(style.color),
        large: size >= 24 || (bold && size >= 18.66),
        rect: {x: box.x, y: box.y, width: box.width, height: box.height},
      })
    }
    return runs
  }) as Promise<Run[]>
}

/** The median pixel behind each run, read off a screenshot taken with every text run hidden. */
async function groundsBehind(page: Page, runs: Run[]): Promise<Rgb[]> {
  await page.addStyleTag({content: "main.mg-main * { color: transparent !important; caret-color: transparent !important; }"})
  const shot = (await page.screenshot()).toString("base64")
  return page.evaluate(async ({image, boxes}) => {
    const picture = new Image()
    picture.src = `data:image/png;base64,${image}`
    await picture.decode()
    const canvas = document.createElement("canvas")
    canvas.width = picture.width
    canvas.height = picture.height
    const ctx = canvas.getContext("2d")!
    ctx.drawImage(picture, 0, 0)
    const scale = picture.width / innerWidth
    return boxes.map((box) => {
      const x = Math.max(0, Math.floor(box.x * scale))
      const y = Math.max(0, Math.floor(box.y * scale))
      const w = Math.max(1, Math.min(canvas.width - x, Math.floor(box.width * scale)))
      const h = Math.max(1, Math.min(canvas.height - y, Math.floor(box.height * scale)))
      const data = ctx.getImageData(x, y, w, h).data
      const lums: Array<{lum: number; rgb: [number, number, number]}> = []
      for (let i = 0; i < data.length; i += 4) {
        const rgb: [number, number, number] = [data[i]!, data[i + 1]!, data[i + 2]!]
        lums.push({lum: rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722, rgb})
      }
      lums.sort((a, b) => a.lum - b.lum)
      return lums[Math.floor(lums.length / 2)]!.rgb
    })
  }, {image: shot, boxes: runs.map((run) => run.rect)}) as Promise<Rgb[]>
}

/** Runs that fall short of WCAG AA: 4.5:1, or 3:1 for large text. */
async function unreadable(page: Page): Promise<string[]> {
  const runs = await textRuns(page)
  const grounds = await groundsBehind(page, runs)
  return runs.flatMap((run, index) => {
    const ratio = contrast(run.ink, grounds[index]!)
    return ratio < (run.large ? 3 : 4.5) ? [`${run.text}: ${ratio.toFixed(2)}:1`] : []
  })
}

const PAGES = [
  {path: "/management", ready: "management-dashboard"},
  {path: "/management/users", ready: "member-manager-list"},
  {path: "/management/recovery", ready: "recovery-list"},
  {path: "/management/contributions", ready: "contributions-page"},
  // Jobs is still drawn with Vuetify's list, whose rows nest buttons in a button; its island
  // rebuild comes with #1907, and it joins this list then.
  {path: "/management/exceptions", ready: "exception-list"},
  {path: "/management/alerts", ready: "alert-list"},
  {path: "/management/mail/sent", ready: "sent-emails"},
]

for (const theme of ["dark", "light"] as const) {
  test.describe(`Management in the ${theme} theme`, () => {
    for (const one of PAGES) {
      test(`${one.path} has no axe violation and every text run reads`, async ({page}) => {
        await page.emulateMedia({colorScheme: theme})
        await page.setViewportSize({width: 1400, height: 900})
        await installApiMocks(page)
        await loginAsAdmin(page.context())
        await page.goto(one.path)
        await page.getByTestId(one.ready).waitFor()

        const axe = await new AxeBuilder({page}).include("main.mg-main").disableRules(["color-contrast"]).analyze()
        expect(axe.violations.map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target.join(" ")).join(", ")}`)).toEqual([])

        expect(await unreadable(page)).toEqual([])
      })
    }
  })
}
