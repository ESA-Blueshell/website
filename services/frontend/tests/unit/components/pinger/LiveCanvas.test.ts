/**
 * The live canvas plays the event's feed and draws our placements over it in their boxes, with a
 * switch between a ghost of the image, just the outline, or nothing. It measures the pixels a pass
 * paints from the images; jsdom has no real canvas, so a stub 2D context and image stand in. A
 * box follows its placement as the stream moves it, and a bouncing one glides on the api's clock.
 */
import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount, type VueWrapper} from "@vue/test-utils"
import LiveCanvas from "@/components/pinger/LiveCanvas.vue"

const hls = vi.hoisted(() => ({
  supported: true,
  loadSource: vi.fn(),
  attachMedia: vi.fn(),
  destroy: vi.fn(),
  on: vi.fn(),
}))

vi.mock("hls.js", () => {
  class Hls {
    static Events = {ERROR: "hlsError"}
    static isSupported = () => hls.supported
    loadSource = hls.loadSource
    attachMedia = hls.attachMedia
    destroy = hls.destroy
    on = hls.on
  }
  return {default: Hls}
})

let images: FakeImage[] = []

class FakeImage {
  public onload: (() => void) | null = null
  public src = ""
  constructor() {
    images.push(this)
  }
}

const loadAll = (): void => images.splice(0).forEach(img => img.onload?.())

const stubCanvas = (getImageData: () => {data: Uint8ClampedArray}): void => {
  const ctx = {drawImage: vi.fn(), getImageData: vi.fn(getImageData)}
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(ctx as unknown as CanvasRenderingContext2D)
  vi.stubGlobal("Image", FakeImage)
}

const still = {mode: "static" as const, vx: 0, vy: 0}
const placement = {id: 1, imageUrl: "/files/public/pinger-paint/1.webp", originX: 1920, originY: 0, width: 960, height: 1080, motion: still, motionEpoch: null}

describe("LiveCanvas", () => {
  const wrappers: VueWrapper[] = []
  const render = () => {
    const wrapper = mount(LiveCanvas, {props: {placements: [placement], streamUrl: "https://tv.example/live.m3u8"}})
    wrappers.push(wrapper)
    return wrapper
  }

  afterEach(() => {
    wrappers.splice(0).forEach(w => w.unmount())
    vi.clearAllMocks()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    images = []
    hls.supported = true
  })

  it("places each placement's box on the canvas", () => {
    const box = render().get("[data-testid=snt-live-box]")

    expect(box.attributes("style")).toContain("left: 50%")
    expect(box.attributes("style")).toContain("width: 25%")
    expect(box.find("img").exists()).toBe(true)
  })

  it("frames each box with its four corner brackets in ghost and outline modes", async () => {
    const wrapper = render()
    expect(wrapper.findAll("[data-testid=snt-live-box] .live__corner")).toHaveLength(4)

    await wrapper.get("[data-testid=snt-live-outline]").trigger("click")
    expect(wrapper.findAll("[data-testid=snt-live-box] .live__corner")).toHaveLength(4)
  })

  it("emits the opaque pixels of every placement as the pass total", async () => {
    const data = new Uint8ClampedArray(600 * 480 * 4)
    for (let i = 0; i < 600 * 480; i += 4) data[i * 4 + 3] = 255
    stubCanvas(() => ({data}))
    const second = {...placement, id: 2, imageUrl: "/files/public/pinger-paint/2.webp"}
    const wrapper = mount(LiveCanvas, {props: {placements: [placement, second], streamUrl: "https://tv.example/live.m3u8"}})
    wrappers.push(wrapper)
    loadAll()

    expect(wrapper.emitted("passtotal")?.at(-1)?.[0]).toBe(2 * (600 * 480) / 4)
  })

  it("counts nothing for an image whose pixels cannot be read", async () => {
    stubCanvas(() => {
      throw new Error("tainted canvas")
    })
    const wrapper = render()
    loadAll()

    expect(wrapper.emitted("passtotal")?.at(-1)?.[0]).toBe(0)
  })

  it("ignores an image that loads after the placements were replaced", async () => {
    stubCanvas(() => ({data: new Uint8ClampedArray(600 * 480 * 4).fill(255)}))
    const wrapper = render()
    await wrapper.setProps({placements: []})
    loadAll()

    expect(wrapper.emitted("passtotal")?.at(-1)?.[0]).toBe(0)
  })

  it("switches the overlay between ghost, outline and off", async () => {
    const wrapper = render()

    await wrapper.get("[data-testid=snt-live-outline]").trigger("click")
    expect(wrapper.get("[data-testid=snt-live-box]").find("img").exists()).toBe(false)

    await wrapper.get("[data-testid=snt-live-off]").trigger("click")
    expect(wrapper.find("[data-testid=snt-live-box]").exists()).toBe(false)
  })

  it("plays the feed through hls.js and lets it go on unmount", async () => {
    const wrapper = render()
    await flushPromises()

    expect(hls.loadSource).toHaveBeenCalledWith("https://tv.example/live.m3u8")
    expect(hls.attachMedia).toHaveBeenCalled()
    wrapper.unmount()
    expect(hls.destroy).toHaveBeenCalled()
  })

  it("says the stream is offline when hls.js gives up", async () => {
    const wrapper = render()
    await flushPromises()
    const onError = hls.on.mock.calls[0][1] as (event: string, data: {fatal: boolean}) => void

    onError("hlsError", {fatal: true})
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain("Stream offline")
  })

  it("says the stream is offline where neither hls.js nor the browser can play it", async () => {
    hls.supported = false
    const wrapper = render()
    await flushPromises()

    expect(wrapper.text()).toContain("Stream offline")
  })

  it("prefers hls.js even where the browser claims it might play HLS itself", async () => {
    vi.spyOn(HTMLMediaElement.prototype, "canPlayType").mockReturnValue("maybe")
    render()
    await flushPromises()

    expect(hls.loadSource).toHaveBeenCalledWith("https://tv.example/live.m3u8")
  })

  it("lets the browser play the feed itself where hls.js cannot run", async () => {
    hls.supported = false
    vi.spyOn(HTMLMediaElement.prototype, "canPlayType").mockReturnValue("maybe")
    const wrapper = render()
    await flushPromises()

    expect((wrapper.get("video").element as HTMLVideoElement).src).toBe("https://tv.example/live.m3u8")
    expect(hls.loadSource).not.toHaveBeenCalled()
  })

  it("shows the feed as live while it plays", async () => {
    const wrapper = render()
    await wrapper.get("video").trigger("playing")
    expect(wrapper.text()).toContain("Live")

    await wrapper.get("video").trigger("waiting")
    expect(wrapper.text()).toContain("Connecting")
  })

  it("follows a placement the stream moved", async () => {
    const wrapper = render()

    await wrapper.setProps({placements: [{...placement, originX: 960, originY: 540}]})

    const style = wrapper.get("[data-testid=snt-live-box]").attributes("style")
    expect(style).toContain("left: 25%")
    expect(style).toContain("top: 25%")
  })

  it("glides a bouncing placement across the plate on the api's clock", async () => {
    vi.useFakeTimers({toFake: ["Date", "requestAnimationFrame", "cancelAnimationFrame"]})
    vi.setSystemTime(Date.parse("2026-10-09T20:00:00Z"))
    try {
      const bouncing = {...placement, originX: 0, originY: 0, motion: {mode: "bounce" as const, vx: 288, vy: 0}, motionEpoch: "2026-10-09T20:00:00Z"}
      // The api's clock runs five seconds ahead, so the box is already 1440px in.
      const wrapper = mount(LiveCanvas, {props: {placements: [bouncing], streamUrl: "https://tv.example/live.m3u8", clockOffset: 5000}})
      wrappers.push(wrapper)
      await wrapper.vm.$nextTick()
      expect(wrapper.get("[data-testid=snt-live-box]").attributes("style")).toContain("left: 37.5%")

      // Frames tick every 16ms, so five seconds on the box sits within a frame of 2880px.
      vi.advanceTimersByTime(5000)
      await wrapper.vm.$nextTick()
      const left = Number(/left: ([\d.]+)%/.exec(wrapper.get("[data-testid=snt-live-box]").attributes("style") ?? "")?.[1])
      expect(left).toBeCloseTo(75, 0)
    } finally {
      vi.useRealTimers()
    }
  })
})
