/**
 * What the pinger manager does that only a browser could reach: loading the paint job into the
 * editor, uploading an image, dragging and resizing the box over the 4K canvas, and the request
 * the Save button builds out of all of it.
 */
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import {VBtn, VFileInput, VSwitch, VTextField} from "vuetify/components"
import PingerManager from "@/pages/management/PingerManager.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const {mockPaint, mockSetPaint, mockUpload} = vi.hoisted(() => ({
  mockPaint: vi.fn(),
  mockSetPaint: vi.fn(),
  mockUpload: vi.fn(),
}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {...actual, paint: mockPaint, setPaint: mockSetPaint, uploadPublicImage: mockUpload}
})

const job = {prefix: "2001:db8:b317:a000::/64", ratePps: 128, originX: 1470, originY: 180, width: 900, height: 720, imageUrl: "/files/public/pinger-paint/old.webp", siteCieEnabled: true}

describe("PingerManager page", () => {
  const wrappers: VueWrapper[] = []

  const mountPage = () => {
    const wrapper = mountInApp(PingerManager)
    wrappers.push(wrapper)
    return wrapper
  }

  const stubStageSize = (wrapper: VueWrapper) => {
    const stage = wrapper.find(".pinger-stage").element as HTMLElement
    stage.getBoundingClientRect = () => ({width: 3840, height: 2160, top: 0, left: 0, right: 3840, bottom: 2160, x: 0, y: 0, toJSON: () => ({})})
  }

  const save = async (wrapper: VueWrapper) => {
    await wrapper.findComponent(VBtn).trigger("click")
    await settle()
    return mockSetPaint.mock.calls.at(-1)?.[0].body
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockPaint.mockResolvedValue({status: 200, data: job})
    mockSetPaint.mockResolvedValue({status: 200, data: job})
    mockUpload.mockResolvedValue({status: 200, data: {path: "pinger-paint/new.webp", url: "x", renditions: []}})
  })

  afterEach(() => unmountAll(wrappers, "PingerManager"))

  it("loads the paint job and saves it back, stripping the public prefix off the image path", async () => {
    const wrapper = mountPage()
    await settle()

    const body = await save(wrapper)

    expect(body).toMatchObject({prefix: job.prefix, ratePps: 128, originX: 1470, originY: 180, width: 900, height: 720, imagePath: "pinger-paint/old.webp", siteCieEnabled: true})
  })

  it("loads the SiteCie toggle and saves it turned off", async () => {
    const wrapper = mountPage()
    await settle()

    wrapper.findComponent(VSwitch).vm.$emit("update:modelValue", false)
    await settle()

    const body = await save(wrapper)
    expect(body.siteCieEnabled).toBe(false)
  })

  it("uploads a chosen image and saves its stored path", async () => {
    const wrapper = mountPage()
    await settle()

    const file = new File(["x"], "logo.png", {type: "image/png"})
    wrapper.findComponent(VFileInput).vm.$emit("update:modelValue", file)
    await settle()

    expect(mockUpload).toHaveBeenCalledOnce()
    const body = await save(wrapper)
    expect(body.imagePath).toBe("pinger-paint/new.webp")
  })

  it("drags the box to a new origin", async () => {
    const wrapper = mountPage()
    await settle()
    stubStageSize(wrapper)

    wrapper.find(".pinger-box").element.dispatchEvent(new MouseEvent("pointerdown", {clientX: 0, clientY: 0, bubbles: true}))
    window.dispatchEvent(new MouseEvent("pointermove", {clientX: 100, clientY: 50}))
    window.dispatchEvent(new MouseEvent("pointerup"))
    await settle()

    const body = await save(wrapper)
    expect(body.originX).toBe(1570)
    expect(body.originY).toBe(230)
  })

  it("resizes the box with its handle", async () => {
    const wrapper = mountPage()
    await settle()
    stubStageSize(wrapper)

    wrapper.find(".pinger-box__handle").element.dispatchEvent(new MouseEvent("pointerdown", {clientX: 0, clientY: 0, bubbles: true}))
    window.dispatchEvent(new MouseEvent("pointermove", {clientX: 200, clientY: 0}))
    window.dispatchEvent(new MouseEvent("pointerup"))
    await settle()

    const body = await save(wrapper)
    expect(body.width).toBe(1100)
  })

  it("drives the paint job from the number and prefix fields", async () => {
    const wrapper = mountPage()
    await settle()

    const [prefix, rate, originX, originY, width, height] = wrapper.findAllComponents(VTextField)
    await prefix.setValue("2001:db8:1::/64")
    await rate.setValue("256")
    await originX.setValue("10")
    await originY.setValue("20")
    await width.setValue("300")
    await height.setValue("400")

    const body = await save(wrapper)
    expect(body).toMatchObject({prefix: "2001:db8:1::/64", ratePps: 256, originX: 10, originY: 20, width: 300, height: 400})
  })

  it("reports when an image upload is refused", async () => {
    mockUpload.mockResolvedValue({error: {detail: "too big"}, data: null})
    const wrapper = mountPage()
    await settle()

    wrapper.findComponent(VFileInput).vm.$emit("update:modelValue", new File(["x"], "logo.png", {type: "image/png"}))
    await settle()

    expect(wrapper.text()).toContain("too big")
  })

  it("shows the reason when a save is refused", async () => {
    mockSetPaint.mockResolvedValue({error: {detail: "nope"}, data: null})
    const wrapper = mountPage()
    await settle()

    await save(wrapper)

    expect(wrapper.text()).toContain("nope")
  })
})
