import {describe, expect, it} from "vitest"
import PastEvents from "@/pages/events/PastEvents.vue"
import {mountInApp} from "../helpers"

describe("Past events page", () => {
  it("heads the archive and leads back to what is coming", () => {
    const wrapper = mountInApp(PastEvents, {global: {stubs: {EventArchive: {template: "<div data-test='archive' />"}}}})

    expect(wrapper.get(".events-head__eyebrow").text()).toBe("The archive")
    expect(wrapper.get("h1").text()).toBe("Past events")
    expect(wrapper.text()).toContain("Search by name or narrow it to one academic year.")
    const upcoming = wrapper.findAllComponents({name: "CutButton"}).find(one => one.props("testid") === "past-events-upcoming")
    expect(upcoming?.props("href")).toBe("/events")
    expect(wrapper.find("[data-test=archive]").exists()).toBe(true)
  })
})
