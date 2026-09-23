import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import BandHead from "@/components/island/BandHead.vue"

describe("BandHead", () => {
  it("draws the eyebrow over the heading, and the way on beside them", () => {
    const wrapper = mount(BandHead, {
      props: {eyebrow: "Come along", heading: "Upcoming events", testid: "head"},
      slots: {default: "<a>All upcoming events</a>"},
    })

    expect(wrapper.find("p").text()).toBe("Come along")
    expect(wrapper.find("h2").text()).toBe("Upcoming events")
    expect(wrapper.find(".band-head__way").text()).toBe("All upcoming events")
  })

  it("puts the count on the heading it counts, with what it counts said aloud", () => {
    const wrapper = mount(BandHead, {
      props: {heading: "Upcoming events", count: 14, countSaid: "upcoming events", testid: "head"},
    })

    const badge = wrapper.find("h2 [data-testid=head-count]")
    expect(badge.text()).toContain("14")
    expect(badge.text()).toContain("upcoming events")
  })

  it("counts without a testid of its own where the band names none", () => {
    const wrapper = mount(BandHead, {props: {heading: "Upcoming events", count: 3}})

    expect(wrapper.findComponent({name: "CountBadge"}).props("testid")).toBeUndefined()
  })

  it("leaves out what it was not given", () => {
    const wrapper = mount(BandHead, {props: {heading: "What we play"}})

    expect(wrapper.find("p").exists()).toBe(false)
    expect(wrapper.findComponent({name: "CountBadge"}).exists()).toBe(false)
    expect(wrapper.find(".band-head__way").exists()).toBe(false)
  })

  it("takes a heading written as markup", () => {
    const wrapper = mount(BandHead, {
      props: {heading: "unused"},
      slots: {heading: "Blueshell's <span class=\"text-brand\">Esports</span>"},
    })

    expect(wrapper.find("h2 .text-brand").text()).toBe("Esports")
  })
})
