import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import CountBadge from "@/components/island/CountBadge.vue"

describe("CountBadge", () => {
  it("shows the count", () => {
    const wrapper = mount(CountBadge, {props: {count: 8}})

    expect(wrapper.text()).toContain("8")
  })

  it("says what the number counts for a reader who cannot see what it sits on", () => {
    const wrapper = mount(CountBadge, {props: {count: 8, said: "upcoming events"}})

    expect(wrapper.find(".island-badge__said").text()).toBe("upcoming events")
  })
})
