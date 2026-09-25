import {describe, expect, it, vi} from "vitest"
import {mount, RouterLinkStub} from "@vue/test-utils"
import PartsGallery from "@/pages/design/PartsGallery.vue"

vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {route: {path: "/design/parts"}})
})

/* The dialog portals to the body; a stand-in draws what it holds where the test can read it. */
const ModalDialog = {
  name: "ModalDialog",
  props: ["open", "title"],
  emits: ["update:open"],
  template: "<div class=\"dialog\"><slot /></div>",
}

const mountGallery = () => mount(PartsGallery, {
  attachTo: document.body,
  global: {stubs: {RouterLink: RouterLinkStub, SliceBand: true, ModalDialog}},
})

describe("the page every island part is drawn on", () => {
  it("draws each part, and reads in both halves of the theme", async () => {
    const wrapper = mountGallery()

    for (const name of ["CutButton", "PageTabs", "CutRow", "FactList", "StateTag", "TaskLayout", "SegmentedChoice", "CountBadge", "PanChevron", "CountryFlag",
      "ModalDialog", "HeaderBand", "BandRule", "LeadBand", "SliceBand", "CallBand"]) {
      expect(wrapper.findComponent({name}).exists(), name).toBe(true)
    }
    expect(wrapper.find(".gallery").classes()).toContain("island-dark")

    await wrapper.find(".gallery__theme").trigger("click")

    expect(wrapper.find(".gallery").classes()).not.toContain("island-dark")
    expect(wrapper.find(".gallery__theme").text()).toBe("Read it dark")
  })

  it("pans the plates as far as they go and no further", async () => {
    const wrapper = mountGallery()
    const plates = () => (wrapper.find(".gallery__plates").element as HTMLElement).style.translate
    const [back, on] = wrapper.findAllComponents({name: "PanChevron"})

    back!.vm.$emit("pan")
    await wrapper.vm.$nextTick()
    expect(plates()).toBe("0rem 0")

    for (let press = 0; press < 5; press++) on!.vm.$emit("pan")
    await wrapper.vm.$nextTick()
    expect(plates()).toBe("-24rem 0")

    back!.vm.$emit("pan")
    await wrapper.vm.$nextTick()
    expect(plates()).toBe("-16rem 0")
  })

  it("keeps the choice that was made, and opens and closes the dialog", async () => {
    const wrapper = mountGallery()

    wrapper.findComponent({name: "SegmentedChoice"}).vm.$emit("update:modelValue", "past")
    await wrapper.vm.$nextTick()
    expect(wrapper.findComponent({name: "SegmentedChoice"}).props("modelValue")).toBe("past")

    const dialog = () => wrapper.findComponent({name: "ModalDialog"})
    await wrapper.findAll("button").find(one => one.text() === "Open the dialog")!.trigger("click")
    expect(dialog().props("open")).toBe(true)
    expect(dialog().text()).toContain("What a dialog says")

    dialog().vm.$emit("update:open", false)
    await wrapper.vm.$nextTick()
    expect(dialog().props("open")).toBe(false)
  })
})
