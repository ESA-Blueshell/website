import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import PagePlaceholder from "@/components/island/PagePlaceholder.vue"

describe("PagePlaceholder", () => {
  it("says it is loading to a reader, and draws as many blank plates as asked", () => {
    const wrapper = mount(PagePlaceholder, {props: {testid: "committee-placeholder", plates: 4}})

    expect(wrapper.get("[data-testid=committee-placeholder]").attributes("aria-busy")).toBe("true")
    expect(wrapper.text()).toBe("Loading")
    expect(wrapper.findAll(".placeholder__plate")).toHaveLength(4)
    expect(wrapper.find("[data-testid=committee-placeholder-band]").exists()).toBe(true)
  })

  it("draws three plates where nobody says how many", () => {
    expect(mount(PagePlaceholder).findAll(".placeholder__plate")).toHaveLength(3)
  })
})
