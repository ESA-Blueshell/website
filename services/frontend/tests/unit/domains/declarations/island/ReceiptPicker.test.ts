import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import ReceiptPicker from "@/domains/declarations/island/ReceiptPicker.vue"
import {uploadReceipt} from "@/domains/declarations/adapters/declarations"

vi.mock("@/domains/declarations/adapters/declarations", () => ({uploadReceipt: vi.fn()}))

const ReceiptCropper = {name: "ReceiptCropper", props: ["src"], emits: ["done", "cancel"], template: "<div />"}
const ImagePicker = {name: "ImagePicker", props: ["picture", "store", "testid", "invalid", "label", "say", "mayBePdf", "shape"], emits: ["update:picture"], template: "<div />"}

const held = {id: 1, path: "1.webp", name: "bill.webp", type: "image/webp", url: "/r/1"}
const stored = {id: 2, path: "2.pdf", name: "shop.pdf", type: "application/pdf", url: "/r/2"}

describe("the receipt picker", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:photo")
    vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined)
  })

  const picker = (props: Record<string, unknown> = {}) =>
    mount(ReceiptPicker, {props: {modelValue: [held], testid: "e0", ...props}, global: {stubs: {ReceiptCropper, ImagePicker}}})
  const adder = (wrapper: ReturnType<typeof picker>) => wrapper.findAllComponents(ImagePicker).at(-1)!
  const storeOf = (wrapper: ReturnType<typeof picker>) => adder(wrapper).props("store") as (file: File) => Promise<{ok: boolean; saved?: unknown; reason?: string}>

  it("holds each receipt in an input of its own and one more to add the next, red when one is missing", () => {
    const wrapper = picker({invalid: true, wrong: "Add at least one receipt."})

    expect(wrapper.findAllComponents(ImagePicker).map(one => one.props("testid"))).toEqual(["e0-receipt-0", "e0-receipts"])
    expect(adder(wrapper).props("invalid")).toBe(true)
    expect(adder(wrapper).props("say")).toBe("Add another receipt")
    expect(wrapper.get("[data-testid=e0-receipts-wrong]").text()).toBe("Add at least one receipt.")
    expect(picker({modelValue: []}).findAllComponents(ImagePicker)[0]!.props("say")).toBe("Add a receipt")
  })

  it("sends a PDF as it is and a photo once it is cropped, and adds what was stored", async () => {
    const wrapper = picker()
    vi.mocked(uploadReceipt).mockResolvedValue({ok: true, saved: stored})

    const shop = new File(["%PDF"], "shop.pdf", {type: "application/pdf"})
    const answer = await storeOf(wrapper)(shop)
    expect(uploadReceipt).toHaveBeenCalledWith(shop, undefined)
    adder(wrapper).vm.$emit("update:picture", answer.saved)
    expect(wrapper.emitted("update:modelValue")!.at(-1)).toEqual([[held, stored]])

    const pending = storeOf(wrapper)(new File(["x"], "bill.jpg", {type: "image/jpeg"}))
    await flushPromises()
    expect(wrapper.getComponent(ReceiptCropper).props("src")).toBe("blob:photo")
    wrapper.getComponent(ReceiptCropper).vm.$emit("done", {x: 0, y: 0, width: 1, height: 1})
    await pending
    expect(uploadReceipt).toHaveBeenLastCalledWith(expect.any(File), {x: 0, y: 0, width: 1, height: 1})
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:photo")
  })

  it("stays as it was when the crop is cancelled, says why a file was refused and takes a receipt off", async () => {
    const wrapper = picker()

    const pending = storeOf(wrapper)(new File(["x"], "bill.jpg", {type: "image/jpeg"}))
    await flushPromises()
    wrapper.getComponent(ReceiptCropper).vm.$emit("cancel")
    await expect(pending).resolves.toEqual({ok: false, reason: ""})
    expect(uploadReceipt).not.toHaveBeenCalled()

    vi.mocked(uploadReceipt).mockResolvedValueOnce({ok: false, reason: "Too large."})
    await expect(storeOf(wrapper)(new File(["%PDF"], "a.pdf", {type: "application/pdf"}))).resolves.toEqual({ok: false, reason: "Too large."})
    vi.mocked(uploadReceipt).mockRejectedValueOnce(new Error("broken"))
    await expect(storeOf(wrapper)(new File(["%PDF"], "a.pdf", {type: "application/pdf"})))
      .resolves.toEqual({ok: false, reason: "We could not read that file. Try a photo or a PDF."})

    wrapper.findAllComponents(ImagePicker)[0]!.vm.$emit("update:picture", null)
    expect(wrapper.emitted("update:modelValue")!.at(-1)).toEqual([[]])
  })
})
