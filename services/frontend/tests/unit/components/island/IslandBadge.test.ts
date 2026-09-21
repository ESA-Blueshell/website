import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import IslandBadge from "@/components/island/IslandBadge.vue"

describe("IslandBadge", () => {
  it("shows the count", () => {
    const wrapper = mount(IslandBadge, {props: {count: 8}})

    expect(wrapper.text()).toContain("8")
  })

  it("says what the number counts for a reader who cannot see what it sits on", () => {
    const wrapper = mount(IslandBadge, {props: {count: 8, said: "upcoming events"}})

    expect(wrapper.find(".island-badge__said").text()).toBe("upcoming events")
  })
})
