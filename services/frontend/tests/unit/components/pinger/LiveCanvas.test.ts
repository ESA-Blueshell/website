/**
 * The live canvas plays the event's feed and draws our placements over it in their boxes, with a
 * switch between a ghost of the image, just the outline, or nothing.
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

const placement = {id: 1, imageUrl: "/files/public/pinger-paint/1.webp", originX: 1920, originY: 0, width: 960, height: 1080}

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
    hls.supported = true
  })

  it("places each placement's box on the canvas", () => {
    const box = render().get("[data-testid=snt-live-box]")

    expect(box.attributes("style")).toContain("left: 50%")
    expect(box.attributes("style")).toContain("width: 25%")
    expect(box.find("img").exists()).toBe(true)
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

  it("says the stream is offline where hls.js cannot run", async () => {
    hls.supported = false
    const wrapper = render()
    await flushPromises()

    expect(wrapper.text()).toContain("Stream offline")
  })

  it("lets the browser play the feed itself where it can", async () => {
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
})
