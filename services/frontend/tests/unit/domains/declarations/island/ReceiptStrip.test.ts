import {beforeAll, describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import ReceiptStrip from "@/domains/declarations/island/ReceiptStrip.vue"
import {ModalDialog, point} from "./modalStub"

const photo = {id: 1, path: "1.webp", name: "bill.webp", type: "image/webp", url: "/r/1"}
const pdf = {id: 2, path: "2.pdf", name: "shop.pdf", type: "application/pdf", url: "/r/2"}

const strip = () => mount(ReceiptStrip, {props: {receipts: [photo, pdf], testid: "rs"}, global: {stubs: {ModalDialog}}})

describe("the receipt strip", () => {
  beforeAll(() => {
    Element.prototype.setPointerCapture = () => undefined
  })

  const scaleOf = (wrapper: ReturnType<typeof strip>) => {
    const transform = (wrapper.get("[data-testid=rs-full]").element as HTMLElement).style.transform
    return Number(/scale\(([\d.]+)\)/.exec(transform)![1])
  }

  const sized = (wrapper: ReturnType<typeof strip>) => {
    const shot = wrapper.get("[data-testid=rs-full]").element as HTMLElement
    Object.defineProperty(shot, "offsetWidth", {value: 300})
    Object.defineProperty(shot, "offsetHeight", {value: 400})
    shot.getBoundingClientRect = () => ({left: 100, top: 50, width: 300, height: 400}) as DOMRect
    const view = wrapper.get("[data-testid=rs-zoom-area]").element as HTMLElement
    Object.defineProperty(view, "clientWidth", {value: 600})
    Object.defineProperty(view, "clientHeight", {value: 500})
    return wrapper.get("[data-testid=rs-zoom-area]")
  }

  it("shows each receipt as a square, a photo by itself and a PDF by name, and opens one large", async () => {
    const wrapper = strip()

    expect(wrapper.get("[data-testid=rs-open-0] img").attributes("src")).toBe("/r/1")
    expect(wrapper.get("[data-testid=rs-open-1]").text()).toBe("PDF")
    await wrapper.get("[data-testid=rs-open-1]").trigger("click")
    expect(wrapper.get("[data-testid=rs-view] object").attributes("data")).toBe("/r/2")

    wrapper.getComponent(ModalDialog).vm.$emit("update:open", true)
    await wrapper.vm.$nextTick()
    expect(wrapper.find("[data-testid=rs-view]").exists()).toBe(true)
    wrapper.getComponent(ModalDialog).vm.$emit("update:open", false)
    await wrapper.vm.$nextTick()
    expect(wrapper.find("[data-testid=rs-view]").exists()).toBe(false)
  })

  it("zooms a photo by wheel, double click, pinch and gesture, and drags it only once zoomed", async () => {
    const wrapper = strip()
    await wrapper.get("[data-testid=rs-open-0]").trigger("click")
    const area = sized(wrapper)

    await point(area, "pointerdown", 200, 200, 1)
    await point(area, "pointermove", 260, 260, 1)
    await point(area, "pointerup", 260, 260, 1)
    expect(scaleOf(wrapper)).toBe(1)

    area.element.dispatchEvent(new WheelEvent("wheel", {deltaY: -100, deltaMode: 0, bubbles: true, cancelable: true}))
    await wrapper.vm.$nextTick()
    expect(scaleOf(wrapper)).toBeGreaterThan(1)
    area.element.dispatchEvent(new WheelEvent("wheel", {deltaY: -10, deltaMode: 1, ctrlKey: true, bubbles: true, cancelable: true}))
    await wrapper.vm.$nextTick()
    area.element.dispatchEvent(new WheelEvent("wheel", {deltaY: 10_000, deltaMode: 0, bubbles: true, cancelable: true}))
    await wrapper.vm.$nextTick()
    expect(scaleOf(wrapper)).toBe(1)

    await area.trigger("dblclick", {clientX: 250, clientY: 250})
    expect(scaleOf(wrapper)).toBe(2.5)
    await point(area, "pointerdown", 200, 200, 1)
    await point(area, "pointermove", 150, 150, 1)
    await point(area, "pointermove", 150, 150, 9)
    await point(area, "pointerup", 150, 150, 1)
    expect((wrapper.get("[data-testid=rs-full]").element as HTMLElement).style.transform).not.toContain("translate(0px, 0px)")

    await point(area, "pointerdown", 200, 200, 1)
    await point(area, "pointerdown", 300, 200, 2)
    await point(area, "pointermove", 400, 200, 2)
    expect(scaleOf(wrapper)).toBe(5)
    await point(area, "pointercancel", 400, 200, 2)
    await point(area, "pointerup", 200, 200, 1)

    await area.trigger("dblclick", {clientX: 250, clientY: 250})
    expect(scaleOf(wrapper)).toBe(1)

    await point(area, "gesturestart")
    await point(area, "gesturechange", 250, 250, 1, {scale: 3})
    expect(scaleOf(wrapper)).toBe(3)
  })

  it("leaves a photo as it is when nothing is drawn yet", async () => {
    const wrapper = strip()
    await wrapper.get("[data-testid=rs-open-0]").trigger("click")
    const area = wrapper.get("[data-testid=rs-zoom-area]")
    ;(wrapper.vm.$.setupState as {shot: unknown}).shot = null

    area.element.dispatchEvent(new WheelEvent("wheel", {deltaY: -100, bubbles: true, cancelable: true}))
    await wrapper.vm.$nextTick()
    expect(scaleOf(wrapper)).toBe(1)
  })
})
