import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import IslandCheck from "@/components/island/IslandCheck.vue"
import IslandFile from "@/components/island/IslandFile.vue"
import IslandRadio from "@/components/island/IslandRadio.vue"

describe("IslandCheck", () => {
  it("is a real checkbox, so the label and the keyboard work", async () => {
    const wrapper = mount(IslandCheck, {props: {modelValue: false, label: "Send me the news"}})
    const box = wrapper.find('input[type="checkbox"]')

    expect(wrapper.find("label").attributes("for")).toBe(box.attributes("id"))

    await box.setValue(true)

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe(true)
  })
})

describe("IslandRadio", () => {
  const options = [{key: "yes", label: "Coming"}, {key: "no", label: "Not coming"}]

  it("reports which one was picked", async () => {
    const wrapper = mount(IslandRadio, {props: {modelValue: null, options, testid: "coming"}})

    await wrapper.find('[data-testid="coming-no"]').setValue(true)

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe("no")
  })

  it("groups them, so picking one lets the other go", () => {
    const wrapper = mount(IslandRadio, {props: {modelValue: "yes", options, name: "coming"}})
    const names = wrapper.findAll("input").map(one => one.attributes("name"))

    expect(new Set(names).size).toBe(1)
  })
})

describe("IslandFile", () => {
  it("says what was chosen, and lets it be taken off again", async () => {
    const chosen = new File(["poster"], "poster.png", {type: "image/png"})
    const wrapper = mount(IslandFile, {props: {modelValue: chosen, testid: "poster"}})

    expect(wrapper.find(".island-file__name").text()).toBe("poster.png")

    await wrapper.find('[data-testid="poster-clear"]').trigger("click")

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBeNull()
  })

  it("keeps the browser's own input, which the plate stands in front of", () => {
    const wrapper = mount(IslandFile, {props: {modelValue: null}})

    expect(wrapper.find('input[type="file"]').exists()).toBe(true)
    expect(wrapper.find("label").attributes("for")).toBe(wrapper.find("input").attributes("id"))
  })

  it("shows a picture as a picture", () => {
    const shot = new File(["x"], "poster.png", {type: "image/png"})
    const wrapper = mount(IslandFile, {props: {modelValue: shot}})

    expect(wrapper.find("img.island-file__shot").exists()).toBe(true)
  })

  it("shows anything else by kind, with its weight", () => {
    const paper = new File(["x".repeat(2048)], "rules.pdf", {type: "application/pdf"})
    const wrapper = mount(IslandFile, {props: {modelValue: paper}})

    expect(wrapper.find("img.island-file__shot").exists()).toBe(false)
    expect(wrapper.find(".island-file__mark").exists()).toBe(true)
    expect(wrapper.find(".island-file__weight").text()).toBe("2 KB")
  })
})
