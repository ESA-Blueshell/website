/*
 * What a canvas board's frame may lack, put back before the app reads it: storage and cookies
 * (a sandboxed frame refuses both), the canvas's today, and pictures pointed at the canvas's own
 * copies instead of an api that is not there.
 */

class MemoryStorage {
  private values = new Map<string, string>()
  get length() { return this.values.size }
  key(index: number) { return [...this.values.keys()][index] ?? null }
  getItem(key: string) { return this.values.has(key) ? this.values.get(key)! : null }
  setItem(key: string, value: string) { this.values.set(key, String(value)) }
  removeItem(key: string) { this.values.delete(key) }
  clear() { this.values.clear() }
}

// Always a board's own copy: the canvas's origin keeps its storage and cookies to itself, and
// every board starts from nothing.
for (const name of ["localStorage", "sessionStorage"] as const) {
  Object.defineProperty(window, name, {value: new MemoryStorage(), configurable: true})
}

const jar = new Map<string, string>()
Object.defineProperty(document, "cookie", {
  configurable: true,
  get: () => [...jar].map(([key, value]) => `${key}=${value}`).join("; "),
  set: (line: string) => {
    const [pair, ...attributes] = String(line).split(";")
    const at = pair.indexOf("=")
    const key = pair.slice(0, at).trim()
    const expired = attributes.some(one => /expires=.*1970/i.test(one) || /max-age=0/i.test(one))
    if (expired) jar.delete(key)
    else jar.set(key, pair.slice(at + 1).trim())
  },
})

/* The canvas's today: the seed's events are dated around it. Time still runs, so motion does. */
const RealDate = Date
const offset = new RealDate("2026-09-21T10:00:00+02:00").getTime() - RealDate.now()
class CanvasDate extends RealDate {
  constructor(...args: unknown[]) {
    if (args.length === 0) super(RealDate.now() + offset)
    else super(...(args as [string]))
  }
  static now() { return RealDate.now() + offset }
}
;(window as unknown as {Date: DateConstructor}).Date = CanvasDate as unknown as DateConstructor

/* Pictures: the seed's art lives on the canvas as blobs named by the same hash. */
// Whatever the frame's origin makes of the api's address (a host, "null" in a sandboxed frame),
// the seed's art is named by a hash the canvas holds an upload under.
const ART = /(?:[a-z][a-z0-9+.-]*:\/\/[^\s"'()]*?|null)?\/api\/files\/public\/[a-z-]+\/([0-9a-f]{32})\.webp/g
// Uploads the sample data invents (board photos, portraits, a freshly chosen picture) have no
// copy on the canvas; they show as a quiet plate rather than a broken image.
const PLATE = `data:image/svg+xml,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 4 3"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#3a4250"/><stop offset="1" stop-color="#23272e"/></linearGradient></defs><rect width="4" height="3" fill="url(#g)"/></svg>')}`
const UPLOAD = /(?:[a-z][a-z0-9+.-]*:\/\/[^\s"'()]*?|null)?\/api\/files\/[^\s"'()]+/g
export const repoint = (text: string) => text
  .replace(ART, (_, hash) => `/_blob/${hash}`)
  .replace(UPLOAD, PLATE)
  .replace(/(^|[\s"'(])\/banner\.webp/g, "$1/_blob/9d6747602c2d2a0b897f64aa886b1b99")
  .replace(/(^|[\s"'(])\/img\//g, "$1live/public/img/")

const fix = (element: Element) => {
  // A board is seen whole, zoomed out; lazy pictures would wait for a scroll that never comes.
  if (element.tagName === "IMG" && element.getAttribute("loading") === "lazy") element.setAttribute("loading", "eager")
  for (const attribute of ["src", "style", "poster", "href"]) {
    const value = element.getAttribute(attribute)
    if (value == null || (attribute === "href" && element.tagName !== "IMAGE")) continue
    const next = repoint(value)
    if (next !== value) element.setAttribute(attribute, next)
  }
  const srcset = element.getAttribute("srcset")
  if (srcset != null && /\/api\/files\//.test(srcset)) element.removeAttribute("srcset")
}
new MutationObserver(records => {
  for (const record of records) {
    if (record.type === "attributes") fix(record.target as Element)
    for (const node of Array.from(record.addedNodes)) {
      if (node instanceof Element) {
        fix(node)
        node.querySelectorAll("img,[src],[srcset],[style]").forEach(fix)
      }
    }
  }
}).observe(document.documentElement, {subtree: true, childList: true, attributes: true, attributeFilter: ["src", "srcset", "style"]})

/* Discord's live socket has nobody to talk to; it stays connecting, and the band reads the widget. */
class QuietSocket extends EventTarget {
  readyState = 0
  url: string
  constructor(url: string) { super(); this.url = url }
  send() {}
  close() { this.readyState = 3 }
}
;(window as unknown as {WebSocket: unknown}).WebSocket = QuietSocket
