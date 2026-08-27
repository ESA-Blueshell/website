import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import TeamSlices from "@/domains/esports/island/TeamSlices.vue"

type Member = {role: string; handle: string; name?: string}

const mountSlices = (members: Member[], extra: Array<{id: number; name: string}> = []) =>
  mount(TeamSlices, {
    props: {
      teams: [
        {id: 1, name: "BS Waterboarders", image: null, members},
        ...extra.map(t => ({...t, image: null, members: []})),
      ] as never,
      accent: "#ff4655",
    },
  })

describe("TeamSlices", () => {
  it("groups the roster as players, substitutes and coaches", () => {
    const wrapper = mountSlices([
      {role: "COACH", handle: "Bobbuz"},
      {role: "PLAYER", handle: "AriosFury"},
      {role: "SUBSTITUTE", handle: "Blackout"},
    ])

    // The order is the roster's own, not the order the api happened to return.
    const labels = wrapper.findAll(".team-slice__group-label").map(n => n.text())
    expect(labels).toEqual(["Player", "Substitute", "Coach"])
  })

  it("names a group in the plural only when it holds more than one", () => {
    const wrapper = mountSlices([
      {role: "PLAYER", handle: "AriosFury"},
      {role: "PLAYER", handle: "Loafine"},
    ])

    expect(wrapper.text()).toContain("Players")
  })

  it("leaves out a group nobody is in", () => {
    const wrapper = mountSlices([{role: "PLAYER", handle: "AriosFury"}])

    expect(wrapper.text()).not.toContain("Coach")
    expect(wrapper.text()).not.toContain("Substitute")
  })

  it("names a member who allows it, beside their handle", () => {
    const wrapper = mountSlices([{role: "PLAYER", handle: "AriosFury", name: "Viktor Petrov"}])

    const member = wrapper.get(".team-slice__member")
    expect(member.text()).toContain("AriosFury")
    expect(member.get(".team-slice__member-name").text()).toBe("Viktor Petrov")
  })

  it("shows the handle alone when no name was given", () => {
    // A name reaches the page only for a member who consented to it; everybody else is
    // their handle and nothing more.
    const wrapper = mountSlices([{role: "PLAYER", handle: "Loafine"}])

    expect(wrapper.get(".team-slice__member").text()).toBe("Loafine")
    expect(wrapper.find(".team-slice__member-name").exists()).toBe(false)
  })

  it("tells apart two members on the same roster", () => {
    const wrapper = mountSlices([
      {role: "PLAYER", handle: "AriosFury", name: "Viktor Petrov"},
      {role: "PLAYER", handle: "Loafine"},
    ])

    expect(wrapper.findAll(".team-slice__member-name")).toHaveLength(1)
  })

  it("says how many are on the roster before it is turned over", () => {
    const wrapper = mountSlices([
      {role: "PLAYER", handle: "AriosFury"},
      {role: "SUBSTITUTE", handle: "Blackout"},
    ])

    expect(wrapper.text()).toContain("2 on the roster")
  })

  it("opens the first slice, and reports which is open", async () => {
    const wrapper = mountSlices([{role: "PLAYER", handle: "AriosFury"}], [{id: 2, name: "BS SpicyWater"}])
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))

    const slices = wrapper.findAll("[data-testid^='team-roster-']")
    expect(slices[0].classes()).toContain("team-slice--open")
    expect(slices[1].classes()).not.toContain("team-slice--open")
    expect(slices[0].get("button").attributes("aria-expanded")).toBe("true")
  })

  it("opens the slice under the pointer instead", async () => {
    const wrapper = mountSlices([{role: "PLAYER", handle: "AriosFury"}], [{id: 2, name: "BS SpicyWater"}])
    const slices = wrapper.findAll("[data-testid^='team-roster-']")

    await slices[1].trigger("mouseenter")

    // A tap has no pointer to hover with, so a click opens a slice the same way.
    expect(slices[1].classes()).toContain("team-slice--open")
    expect(slices[0].classes()).not.toContain("team-slice--open")
  })
})
