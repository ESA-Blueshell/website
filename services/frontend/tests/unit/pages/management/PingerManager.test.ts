/**
 * The pinger manager: loading the paint job, editing the settings, adding and removing images, and
 * dragging and resizing a placement's box over the 4K canvas, setting how a placement moves, and
 * following the paint stream, with the domain calls each makes. The event's live stream plays behind
 * the boxes through a mocked hls.js.
 */
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import PingerManager from "@/pages/management/PingerManager.vue"
import {mountInApp, settle, unmountAll} from "../../helpers/testUtils"

const {mockLoad, mockSave, mockStore, mockAdd, mockMove, mockRemove, mockMotion, mockOpenPaint} = vi.hoisted(() => ({
  mockMotion: vi.fn(),
  mockOpenPaint: vi.fn(),
  mockLoad: vi.fn(),
  mockSave: vi.fn(),
  mockStore: vi.fn(),
  mockAdd: vi.fn(),
  mockMove: vi.fn(),
  mockRemove: vi.fn(),
}))

const hls = vi.hoisted(() => ({loadSource: vi.fn(), attachMedia: vi.fn(), destroy: vi.fn(), on: vi.fn()}))

vi.mock("hls.js", () => {
  class Hls {
    static Events = {ERROR: "hlsError"}
    static isSupported = () => true
    loadSource = hls.loadSource
    attachMedia = hls.attachMedia
    destroy = hls.destroy
    on = hls.on
  }
  return {default: Hls}
})

vi.mock("@/domains/pinger", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/domains/pinger")>()
  return {
    ...actual,
    loadPaintJob: mockLoad,
    saveSettings: mockSave,
    storePaintImage: mockStore,
    addPlacement: mockAdd,
    movePlacement: mockMove,
    removePlacement: mockRemove,
    setMotion: mockMotion,
    openPaintStream: mockOpenPaint,
  }
})

class FakeImage {
  public onload: (() => void) | null = null
  public onerror: (() => void) | null = null
  public naturalWidth = 900
  public naturalHeight = 720
  public set src(_v: string) {
    queueMicrotask(() => this.onload?.())
  }
}

const job = {
  prefix: "2001:db8:b317:a000::/64",
  ratePps: 128,
  siteCieEnabled: true,
  serverTime: null,
  placements: [{
    id: 5, imageUrl: "/files/public/pinger-paint/old.webp", originX: 1470, originY: 180, width: 900, height: 720,
    motion: {mode: "static" as const, vx: 0, vy: 0}, motionEpoch: null,
  }],
}
type Job = typeof job

describe("PingerManager page", () => {
  const wrappers: VueWrapper[] = []
  let paintPush: ((next: Job) => void) | null = null

  const mount = async () => {
    const wrapper = mountInApp(PingerManager)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  const stubStage = (wrapper: VueWrapper) => {
    const stage = wrapper.find(".stage").element as HTMLElement
    stage.getBoundingClientRect = () => ({width: 3840, height: 2160, top: 0, left: 0, right: 3840, bottom: 2160, x: 0, y: 0, toJSON: () => ({})})
  }

  const addFile = async (wrapper: VueWrapper) => {
    const input = wrapper.find("input[type=file]")
    const file = new File(["x"], "logo.png", {type: "image/png"})
    Object.defineProperty(input.element, "files", {value: [file], configurable: true})
    await input.trigger("change")
    await settle()
  }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal("Image", FakeImage)
    mockLoad.mockResolvedValue(job)
    mockSave.mockResolvedValue({ok: true, saved: job})
    mockStore.mockResolvedValue({ok: true, saved: "pinger-paint/new.webp"})
    mockAdd.mockResolvedValue({ok: true, saved: {id: 9, imageUrl: "/files/public/pinger-paint/new.webp", originX: 1200, originY: 500, width: 900, height: 675, motion: job.placements[0].motion, motionEpoch: null}})
    paintPush = null
    mockOpenPaint.mockImplementation((cb: (next: Job) => void) => {
      paintPush = cb
      return vi.fn()
    })
    mockMove.mockResolvedValue({ok: true, saved: job.placements[0]})
    mockRemove.mockResolvedValue({ok: true, saved: undefined})
  })

  afterEach(() => {
    unmountAll(wrappers, "PingerManager")
    vi.unstubAllGlobals()
  })

  it("loads the job and lists the placement with its box and the settings", async () => {
    const wrapper = await mount()

    expect(wrapper.findAll("[data-testid=pinger-placement-row]")).toHaveLength(1)
    expect(wrapper.get("[data-testid=pinger-placement-coords]").text()).toContain("1470, 180")
    expect((wrapper.get("[data-testid=pinger-prefix] input").element as HTMLInputElement).value).toBe(job.prefix)
  })

  it("saves the settings, trimming the prefix off the empty-is-null rule", async () => {
    const wrapper = await mount()

    await wrapper.get("[data-testid=pinger-save]").trigger("click")
    await settle()

    expect(mockSave).toHaveBeenCalledWith({prefix: job.prefix, ratePps: 128, siteCieEnabled: true})
    expect(wrapper.text()).toContain("Saved")
  })

  it("edits the prefix and rate and saves them", async () => {
    const wrapper = await mount()

    await wrapper.find("[data-testid=pinger-prefix] input").setValue("2001:db8:1::/64")
    await wrapper.find("[data-testid=pinger-rate] input").setValue("256")
    await wrapper.get("[data-testid=pinger-save]").trigger("click")
    await settle()

    expect(mockSave).toHaveBeenCalledWith({prefix: "2001:db8:1::/64", ratePps: 256, siteCieEnabled: true})
  })

  it("turns SiteCie off and saves it", async () => {
    const wrapper = await mount()

    await wrapper.find("[data-testid=pinger-sitecie]").setValue(false)
    await wrapper.get("[data-testid=pinger-save]").trigger("click")
    await settle()

    expect(mockSave).toHaveBeenCalledWith(expect.objectContaining({siteCieEnabled: false}))
  })

  it("adds a chosen image, uploading it and placing it on the canvas", async () => {
    const wrapper = await mount()

    await addFile(wrapper)

    expect(mockStore).toHaveBeenCalledOnce()
    expect(mockAdd).toHaveBeenCalledWith("pinger-paint/new.webp", expect.objectContaining({width: 900}))
    expect(wrapper.findAll("[data-testid=pinger-placement-row]")).toHaveLength(2)
  })

  it("reports a refused upload", async () => {
    mockStore.mockResolvedValue({ok: false, reason: "too big"})
    const wrapper = await mount()

    await addFile(wrapper)

    expect(mockAdd).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain("too big")
  })

  it("reports a refused placement add", async () => {
    mockAdd.mockResolvedValue({ok: false, reason: "no room"})
    const wrapper = await mount()

    await addFile(wrapper)

    expect(wrapper.text()).toContain("no room")
  })

  it("removes a placement", async () => {
    const wrapper = await mount()

    await wrapper.get("[data-testid=pinger-placement-remove]").trigger("click")
    await settle()

    expect(mockRemove).toHaveBeenCalledWith(5)
    expect(wrapper.findAll("[data-testid=pinger-placement-row]")).toHaveLength(0)
  })

  it("reports a refused save", async () => {
    mockSave.mockResolvedValue({ok: false, reason: "nope"})
    const wrapper = await mount()

    await wrapper.get("[data-testid=pinger-save]").trigger("click")
    await settle()

    expect(wrapper.text()).toContain("nope")
  })

  it("drags a placement to a new origin", async () => {
    const wrapper = await mount()
    stubStage(wrapper)

    wrapper.get("[data-testid=pinger-placement]").element
      .dispatchEvent(new MouseEvent("pointerdown", {clientX: 0, clientY: 0, bubbles: true}))
    window.dispatchEvent(new MouseEvent("pointermove", {clientX: 100, clientY: 50}))
    window.dispatchEvent(new MouseEvent("pointerup"))
    await settle()

    expect(mockMove).toHaveBeenCalledWith(5, expect.objectContaining({originX: 1570, originY: 230}))
  })

  it("reports a refused move after a drag", async () => {
    mockMove.mockResolvedValue({ok: false, reason: "out of bounds"})
    const wrapper = await mount()
    stubStage(wrapper)

    wrapper.get("[data-testid=pinger-placement]").element
      .dispatchEvent(new MouseEvent("pointerdown", {clientX: 0, clientY: 0, bubbles: true}))
    window.dispatchEvent(new MouseEvent("pointermove", {clientX: 100, clientY: 50}))
    window.dispatchEvent(new MouseEvent("pointerup"))
    await settle()

    expect(wrapper.text()).toContain("out of bounds")
  })

  it("resizes a placement with its handle, holding the image ratio once loaded", async () => {
    const wrapper = await mount()
    stubStage(wrapper)
    // The box image loads, so the resize holds its ratio.
    await wrapper.find(".box__img").trigger("load")

    wrapper.find(".box__handle").element
      .dispatchEvent(new MouseEvent("pointerdown", {clientX: 0, clientY: 0, bubbles: true}))
    window.dispatchEvent(new MouseEvent("pointermove", {clientX: 200, clientY: 0}))
    window.dispatchEvent(new MouseEvent("pointerup"))
    await settle()

    expect(mockMove).toHaveBeenCalledWith(5, expect.objectContaining({width: 1100}))
  })

  it("opens the file picker from the add tile", async () => {
    const wrapper = await mount()
    const input = wrapper.find("input[type=file]").element as HTMLInputElement
    const click = vi.spyOn(input, "click").mockImplementation(() => {})

    await wrapper.get("[data-testid=pinger-add]").trigger("click")

    expect(click).toHaveBeenCalled()
  })

  it("selects a placement when its row is clicked", async () => {
    const wrapper = await mount()

    await wrapper.get("[data-testid=pinger-placement-row]").trigger("click")

    expect(wrapper.get("[data-testid=pinger-placement-row]").classes()).toContain("plate--selected")
  })

  it("reports a refused remove", async () => {
    mockRemove.mockResolvedValue({ok: false, reason: "still in use"})
    const wrapper = await mount()

    await wrapper.get("[data-testid=pinger-placement-remove]").trigger("click")
    await settle()

    expect(wrapper.text()).toContain("still in use")
    expect(wrapper.findAll("[data-testid=pinger-placement-row]")).toHaveLength(1)
  })

  it("falls back to a default box when the chosen image cannot be read", async () => {
    class ErrorImage {
      public onload: (() => void) | null = null
      public onerror: (() => void) | null = null
      public set src(_v: string) {
        queueMicrotask(() => this.onerror?.())
      }
    }
    vi.stubGlobal("Image", ErrorImage)
    vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {})
    const wrapper = await mount()

    await addFile(wrapper)

    // ratio null, so the box takes the 4:3 default: 900 wide, 675 tall.
    expect(mockAdd).toHaveBeenCalledWith("pinger-paint/new.webp", expect.objectContaining({width: 900, height: 675}))
  })

  it("holds a tall image's ratio on resize and clamps the box to the canvas", async () => {
    const wrapper = await mount()
    stubStage(wrapper)
    const img = wrapper.find(".box__img").element
    Object.defineProperty(img, "naturalWidth", {value: 800, configurable: true})
    Object.defineProperty(img, "naturalHeight", {value: 1000, configurable: true})
    await wrapper.find(".box__img").trigger("load")

    wrapper.find(".box__handle").element
      .dispatchEvent(new MouseEvent("pointerdown", {clientX: 0, clientY: 0, bubbles: true}))
    window.dispatchEvent(new MouseEvent("pointermove", {clientX: 2000, clientY: 0}))
    window.dispatchEvent(new MouseEvent("pointerup"))
    await settle()

    expect(mockMove).toHaveBeenCalledWith(5, expect.objectContaining({width: 1584, height: 1980}))
  })

  it("saves a placement's motion from its row", async () => {
    const moving = {...job.placements[0], motion: {mode: "bounce" as const, vx: 300, vy: -120}, motionEpoch: "2026-10-09T20:00:00Z"}
    mockMotion.mockResolvedValue({ok: true, saved: moving})
    const wrapper = await mount()

    await wrapper.get("[data-testid=pinger-motion-mode-bounce]").setValue(true)
    expect((wrapper.get("[data-testid=pinger-motion-vx] input").element as HTMLInputElement).value).toBe("240")
    await wrapper.get("[data-testid=pinger-motion-vx] input").setValue("300")
    await wrapper.get("[data-testid=pinger-motion-vy] input").setValue("-120")
    await wrapper.get("[data-testid=pinger-motion-save]").trigger("click")
    await settle()

    expect(mockMotion).toHaveBeenCalledWith(5, {mode: "bounce", vx: 300, vy: -120})
  })

  it("reports a refused motion", async () => {
    mockMotion.mockResolvedValue({ok: false, reason: "too fast"})
    const wrapper = await mount()

    await wrapper.get("[data-testid=pinger-motion-save]").trigger("click")
    await settle()

    expect(mockMotion).toHaveBeenCalledWith(5, {mode: "static", vx: 240, vy: 160})
    expect(wrapper.text()).toContain("too fast")
  })

  it("follows the paint stream's boxes and leaves the settings as the admin has them", async () => {
    const wrapper = await mount()
    await wrapper.find("[data-testid=pinger-prefix] input").setValue("2001:db8:1::/64")

    paintPush!({...job, prefix: "other", placements: [{...job.placements[0], originX: 2000, originY: 300}]})
    await settle()

    expect(wrapper.get("[data-testid=pinger-placement-coords]").text()).toContain("2000, 300")
    expect((wrapper.get("[data-testid=pinger-prefix] input").element as HTMLInputElement).value).toBe("2001:db8:1::/64")
  })

  it("plays the event's live stream behind the boxes and ghosts them while it is live", async () => {
    const wrapper = await mount()

    expect(hls.loadSource).toHaveBeenCalledWith("https://tv.pings.utwente.io/1080p30_hls.m3u8")
    expect(hls.attachMedia).toHaveBeenCalledWith(wrapper.get("[data-testid=pinger-live]").element)
    expect(wrapper.get("[data-testid=pinger-live-badge]").text()).toBe("Connecting")

    await wrapper.get("[data-testid=pinger-live]").trigger("playing")

    expect(wrapper.get("[data-testid=pinger-live-badge]").text()).toBe("Live")
    expect(wrapper.get("[data-testid=pinger-placement]").classes()).toContain("box--live")
  })

  it("hides the stream once it fails and keeps the boxes draggable on the plain plate", async () => {
    const wrapper = await mount()
    stubStage(wrapper)
    const onError = hls.on.mock.calls[0][1] as (event: string, data: {fatal: boolean}) => void

    onError("hlsError", {fatal: true})
    await settle()

    expect(wrapper.get("[data-testid=pinger-live-badge]").text()).toBe("Stream offline")
    expect((wrapper.get("[data-testid=pinger-live]").element as HTMLElement).style.display).toBe("none")
    wrapper.get("[data-testid=pinger-placement]").element
      .dispatchEvent(new MouseEvent("pointerdown", {clientX: 0, clientY: 0, bubbles: true}))
    window.dispatchEvent(new MouseEvent("pointermove", {clientX: 100, clientY: 50}))
    window.dispatchEvent(new MouseEvent("pointerup"))
    await settle()
    expect(mockMove).toHaveBeenCalledWith(5, expect.objectContaining({originX: 1570, originY: 230}))
  })
})
