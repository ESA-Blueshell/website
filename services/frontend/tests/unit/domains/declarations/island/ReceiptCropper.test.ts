import {beforeAll, describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import ReceiptCropper from "@/domains/declarations/island/ReceiptCropper.vue"
import {ModalDialog, point} from "./modalStub"

const cropper = () => mount(ReceiptCropper, {props: {src: "blob:photo"}, global: {stubs: {ModalDialog}}})

describe("the receipt cropper", () => {
  beforeAll(() => {
    Element.prototype.setPointerCapture = () => undefined
  })

  const stage = (wrapper: ReturnType<typeof cropper>) => {
    const element = wrapper.get(".crop__stage").element as HTMLElement
    element.getBoundingClientRect = () => ({left: 0, top: 0, width: 100, height: 200}) as DOMRect
    return wrapper.get(".crop__stage")
  }

  it("moves the frame by a drag and sizes it by its corners, never past the photo or below its smallest", async () => {
    const wrapper = cropper()
    const area = stage(wrapper)

    await point(wrapper.get("[data-testid=receipt-crop-frame]"), "pointerdown", 50, 100)
    await point(area, "pointermove", 100, 100)
    await point(area, "pointerup")
    await point(area, "pointermove", 0, 0)
    await wrapper.get("[data-testid=receipt-crop-done]").trigger("click")
    expect(wrapper.emitted("done")!.at(-1)![0]).toMatchObject({x: 0.12, y: 0.06, width: 0.88})

    await point(wrapper.get("[data-testid=receipt-crop-nw]"), "pointerdown", 0, 0)
    await point(area, "pointermove", 200, 400)
    await point(area, "pointercancel")
    await point(wrapper.get("[data-testid=receipt-crop-se]"), "pointerdown", 0, 0)
    await point(area, "pointermove", -500, -500)
    await wrapper.get("[data-testid=receipt-crop-done]").trigger("click")
    const smallest = wrapper.emitted("done")!.at(-1)![0] as {width: number; height: number}
    expect(smallest.width).toBeCloseTo(0.08)
    expect(smallest.height).toBeCloseTo(0.08)

    await point(wrapper.get("[data-testid=receipt-crop-ne]"), "pointerdown", 0, 0)
    await point(area, "pointermove", 500, -500)
    await point(wrapper.get("[data-testid=receipt-crop-sw]"), "pointerdown", 0, 0)
    await point(area, "pointermove", -500, 500)
    await wrapper.get("[data-testid=receipt-crop-done]").trigger("click")
    expect(wrapper.emitted("done")!.at(-1)![0]).toEqual({x: 0, y: 0, width: 1, height: 1})
  })

  it("keeps the whole photo on request, starts afresh for the next one and is closed by cancelling", async () => {
    const wrapper = cropper()

    await wrapper.get("[data-testid=receipt-crop-whole]").trigger("click")
    expect(wrapper.emitted("done")![0]).toEqual([{x: 0, y: 0, width: 1, height: 1}])
    await wrapper.setProps({src: "blob:next"})
    await wrapper.get("[data-testid=receipt-crop-done]").trigger("click")
    expect(wrapper.emitted("done")![1]).toEqual([{x: 0.06, y: 0.06, width: 0.88, height: 0.88}])

    wrapper.getComponent(ModalDialog).vm.$emit("update:open", true)
    wrapper.getComponent(ModalDialog).vm.$emit("update:open", false)
    expect(wrapper.emitted("cancel")).toHaveLength(1)
  })
})
