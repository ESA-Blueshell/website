import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import TrackTitle from "@/domains/vote/island/TrackTitle.vue"

describe("a song's title", () => {
  const titled = (renamable: boolean) => mount(TrackTitle, {props: {title: "Celeste OST - Resurrections", renamable, testid: "title"}, attachTo: document.body})

  it("is only its words for a member who may not rename it", () => {
    const wrapper = titled(false)

    expect(wrapper.get("[data-testid=title]").element.tagName).toBe("SPAN")
    expect(wrapper.text()).toBe("Celeste OST - Resurrections")
    wrapper.unmount()
  })

  it("becomes a field holding the title when pressed, and Enter hands the new one up, trimmed", async () => {
    const wrapper = titled(true)

    await wrapper.get("[data-testid=title]").trigger("click")
    const field = wrapper.get("[data-testid=title-field]")
    expect((field.element as HTMLInputElement).value).toBe("Celeste OST - Resurrections")
    await field.setValue("  Resurrections ")
    await field.trigger("keydown", {key: "Enter"})

    expect(wrapper.emitted("rename")).toEqual([["Resurrections"]])
    expect(wrapper.find("[data-testid=title-field]").exists()).toBe(false)
    wrapper.unmount()
  })

  it("saves on leaving the field, and hands up nothing for a title left blank, unchanged or put back by Escape", async () => {
    const wrapper = titled(true)
    const edit = async (typed: string, end: "blur" | "Escape") => {
      await wrapper.get("[data-testid=title]").trigger("click")
      const field = wrapper.get("[data-testid=title-field]")
      await field.setValue(typed)
      if (end === "blur") await field.trigger("blur")
      else await field.trigger("keydown", {key: "Escape"})
    }

    await edit("   ", "blur")
    await edit("Celeste OST - Resurrections", "blur")
    await edit("Gone", "Escape")
    expect(wrapper.emitted("rename")).toBeUndefined()

    await edit("First Steps", "blur")
    expect(wrapper.emitted("rename")).toEqual([["First Steps"]])
    wrapper.unmount()
  })
})
