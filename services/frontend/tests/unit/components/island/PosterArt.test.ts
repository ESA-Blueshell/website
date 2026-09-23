import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import PosterArt from "@/components/island/PosterArt.vue"

describe("PosterArt", () => {
  it("draws the poster somebody made, uncut, and no plate", () => {
    const wrapper = mount(PosterArt, {props: {title: "LAN", banner: "/lan.webp", alt: "LAN", width: 1080, height: 1080}})

    expect(wrapper.get("img").attributes()).toMatchObject({src: "/lan.webp", alt: "LAN"})
    expect(wrapper.find(".poster-art__plate").exists()).toBe(false)
  })

  it("draws a date plate where there is no poster: the day, the month, the name, the hours and the place, over the rule", () => {
    const wrapper = mount(PosterArt, {props: {title: "Pub quiz", day: "2", month: "Oct", when: "19:00-22:00", where: "Café De Beiaard"}})

    expect(wrapper.get(".poster-art__day").text()).toBe("2")
    expect(wrapper.get(".poster-art__month").text()).toBe("Oct")
    expect(wrapper.get(".poster-art__title").text()).toBe("Pub quiz")
    expect(wrapper.findAll(".poster-art__line").map(one => one.text())).toEqual(["19:00-22:00", "Café De Beiaard"])
    expect(wrapper.find(".poster-art__rule").exists()).toBe(true)
    expect(wrapper.get(".poster-art__rule").attributes("data-testid")).toBeUndefined()
  })

  it("leaves out what it is not told: no date without a day, no month, no hours or place", () => {
    const bare = mount(PosterArt, {props: {title: "Your event"}})
    const dayOnly = mount(PosterArt, {props: {title: "LAN", day: "9"}})

    expect(bare.find(".poster-art__date").exists()).toBe(false)
    expect(bare.findAll(".poster-art__line")).toHaveLength(0)
    expect(bare.get(".poster-art__title").text()).toBe("Your event")
    expect(dayOnly.get(".poster-art__day").text()).toBe("9")
    expect(dayOnly.find(".poster-art__month").exists()).toBe(false)
  })
})
