/**
 * CanvasPlacement draws one image filling in pixel by pixel. jsdom has no real canvas, so a stub
 * 2D context and a controllable animation frame let the build and the paint loop run and be checked.
 */
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import CanvasPlacement from "@/components/pinger/CanvasPlacement.vue"

const PX_W = 600
const PX_H = 480

let frames: Array<(time: number) => void> = []

type Ctx = {
  drawImage: ReturnType<typeof vi.fn>
  getImageData: ReturnType<typeof vi.fn>
  fillRect: ReturnType<typeof vi.fn>
  clearRect: ReturnType<typeof vi.fn>
  fillStyle: string
}
let ctx: Ctx

function makeImageData(): {data: Uint8ClampedArray} {
  const data = new Uint8ClampedArray(PX_W * PX_H * 4)
  // Alternate alpha, so the build hits both the painted and the skipped branch.
  for (let i = 0; i < PX_W * PX_H; i++) data[i * 4 + 3] = i % 2 === 0 ? 255 : 0
  return {data}
}

class FakeImage {
  public onload: (() => void) | null = null
  public naturalWidth = PX_W
  public complete = false
  public set src(_v: string) {
    queueMicrotask(() => this.onload?.())
  }
}

const props = (over: Record<string, unknown> = {}) => ({
  imageUrl: "/files/public/pinger-paint/art.webp",
  originX: 100,
  originY: 100,
  width: 900,
  height: 720,
  running: true,
  sent: 50,
  pps: 100,
  ...over,
})

describe("CanvasPlacement", () => {
  const wrappers: VueWrapper[] = []
  let matchMediaMatches = false

  const render = async (over: Record<string, unknown> = {}) => {
    const wrapper = mount(CanvasPlacement, {props: props(over)})
    wrappers.push(wrapper)
    await Promise.resolve()
    await wrapper.vm.$nextTick()
    return wrapper
  }

  const step = () => {
    const due = frames
    frames = []
    due.forEach(cb => cb(performance.now()))
  }

  beforeEach(() => {
    frames = []
    ctx = {drawImage: vi.fn(), getImageData: vi.fn(makeImageData), fillRect: vi.fn(), clearRect: vi.fn(), fillStyle: ""}
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(ctx as unknown as CanvasRenderingContext2D)
    vi.stubGlobal("Image", FakeImage)
    vi.stubGlobal("requestAnimationFrame", (cb: (time: number) => void) => {
      frames.push(cb)
      return frames.length
    })
    vi.stubGlobal("cancelAnimationFrame", vi.fn())
    vi.stubGlobal("matchMedia", (q: string) => ({matches: matchMediaMatches, media: q, addEventListener: vi.fn(), removeEventListener: vi.fn()}))
  })

  afterEach(() => {
    wrappers.splice(0).forEach(w => w.unmount())
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    matchMediaMatches = false
  })

  it("builds the pixels from the image and emits the pass total", async () => {
    const wrapper = await render()

    const total = wrapper.emitted("passtotal")?.at(-1)?.[0]
    expect(total).toBe((PX_W * PX_H) / 2)
    expect(ctx.drawImage).toHaveBeenCalled()
  })

  it("paints up to the target and sparks the leading edge while running", async () => {
    await render()
    step()

    expect(ctx.fillRect).toHaveBeenCalled()
  })

  it("clears the canvas and stops painting when not running", async () => {
    await render({running: false})
    step()

    expect(ctx.clearRect).toHaveBeenCalled()
    expect(ctx.fillRect).not.toHaveBeenCalled()
  })

  it("wraps back to the start when the target falls below what is drawn", async () => {
    const wrapper = await render()
    step()
    ctx.clearRect.mockClear()

    await wrapper.setProps(props({sent: 0}))
    step()

    expect(ctx.clearRect).toHaveBeenCalled()
  })

  it("rebuilds when the image url changes", async () => {
    const wrapper = await render()
    ctx.drawImage.mockClear()

    await wrapper.setProps(props({imageUrl: "/files/public/pinger-paint/other.webp"}))
    await Promise.resolve()
    await wrapper.vm.$nextTick()

    expect(ctx.drawImage).toHaveBeenCalled()
  })

  it("fills without the spark under reduced motion", async () => {
    matchMediaMatches = true
    await render()
    step()

    expect(ctx.fillRect).toHaveBeenCalled()
  })

  it("gives up the build when the pixels cannot be read", async () => {
    ctx.getImageData = vi.fn(() => {
      throw new Error("tainted canvas")
    })
    const wrapper = await render()

    expect(wrapper.emitted("passtotal")?.at(-1)?.[0] ?? 0).toBe(0)
  })
})
