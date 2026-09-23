import {afterEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import EventPreview from "@/domains/events/island/EventPreview.vue"

describe("the event form's preview", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("writes the name, the day and the place onto the template while there is no poster", () => {
    const wrapper = mount(EventPreview, {props: {title: " Pub quiz ", location: "Café De Beiaard", startTime: "2099-10-02T19:00:00"}})

    expect(wrapper.get(".preview__title").text()).toBe("Pub quiz")
    expect(wrapper.get(".preview__when").text()).toBe("Fri 2 October - 19:00")
    expect(wrapper.getComponent({name: "PosterArt"}).props("banner")).toBeUndefined()
  })

  it("names an event nobody has named yet, and says no day it cannot read", () => {
    const wrapper = mount(EventPreview, {props: {title: ""}})

    expect(wrapper.get(".preview__title").text()).toBe("Your event")
    expect(wrapper.get(".preview__when").text()).toBe("")
    expect(wrapper.getComponent({name: "PosterArt"}).props("where")).toBeUndefined()
  })

  it("draws the poster picked, and lets go of each one it drew", async () => {
    const createObjectURL = vi.fn().mockReturnValueOnce("blob:one").mockReturnValueOnce("blob:two")
    const revokeObjectURL = vi.fn()
    vi.stubGlobal("URL", {...URL, createObjectURL, revokeObjectURL})
    const wrapper = mount(EventPreview, {props: {title: "LAN", poster: new File(["a"], "a.webp")}})

    expect(wrapper.getComponent({name: "PosterArt"}).props("banner")).toBe("blob:one")
    await wrapper.setProps({poster: new File(["b"], "b.webp")})
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:one")
    await wrapper.setProps({poster: null})
    wrapper.unmount()
    expect(revokeObjectURL).toHaveBeenCalledTimes(2)
  })
})
