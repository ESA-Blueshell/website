import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import TeamFlipCard from "@/domains/esports/island/TeamFlipCard.vue"

type Member = {role: string; handle: string; name?: string}

const mountCard = (members: Member[]) =>
  mount(TeamFlipCard, {
    props: {team: {id: 1, name: "BS Waterboarders", image: null, members} as never, accent: "#ff4655"},
  })

describe("TeamFlipCard", () => {
  it("groups the roster as players, substitutes and coaches", () => {
    const wrapper = mountCard([
      {role: "COACH", handle: "Bobbuz"},
      {role: "PLAYER", handle: "AriosFury"},
      {role: "SUBSTITUTE", handle: "Blackout"},
    ])

    // The order is the roster's own, not the order the api happened to return.
    const labels = wrapper.findAll(".team-card__face--back span.uppercase").map(n => n.text())
    expect(labels.filter(l => ["Player", "Substitute", "Coach"].includes(l)))
      .toEqual(["Player", "Substitute", "Coach"])
  })

  it("names a group in the plural only when it holds more than one", () => {
    const wrapper = mountCard([
      {role: "PLAYER", handle: "AriosFury"},
      {role: "PLAYER", handle: "Loafine"},
    ])

    expect(wrapper.text()).toContain("Players")
  })

  it("leaves out a group nobody is in", () => {
    const wrapper = mountCard([{role: "PLAYER", handle: "AriosFury"}])

    expect(wrapper.text()).not.toContain("Coach")
    expect(wrapper.text()).not.toContain("Substitute")
  })

  it("names a member who allows it, beside their handle", () => {
    const wrapper = mountCard([{role: "PLAYER", handle: "AriosFury", name: "Viktor Petrov"}])

    const member = wrapper.get(".team-card__member")
    expect(member.text()).toContain("AriosFury")
    expect(member.get(".team-card__member-name").text()).toBe("Viktor Petrov")
  })

  it("shows the handle alone when no name was given", () => {
    // A name reaches the page only for a member who consented to it; everybody else is
    // their handle and nothing more.
    const wrapper = mountCard([{role: "PLAYER", handle: "Loafine"}])

    expect(wrapper.get(".team-card__member").text()).toBe("Loafine")
    expect(wrapper.find(".team-card__member-name").exists()).toBe(false)
  })

  it("tells apart two members on the same roster", () => {
    const wrapper = mountCard([
      {role: "PLAYER", handle: "AriosFury", name: "Viktor Petrov"},
      {role: "PLAYER", handle: "Loafine"},
    ])

    expect(wrapper.findAll(".team-card__member-name")).toHaveLength(1)
  })

  it("says how many are on the roster before it is turned over", () => {
    const wrapper = mountCard([
      {role: "PLAYER", handle: "AriosFury"},
      {role: "SUBSTITUTE", handle: "Blackout"},
    ])

    expect(wrapper.text()).toContain("2 on the roster")
  })

  it("reports whether it is turned over, for anything reading it aloud", async () => {
    const wrapper = mountCard([{role: "PLAYER", handle: "AriosFury"}])
    const button = wrapper.get("button")
    expect(button.attributes("aria-expanded")).toBe("false")

    await button.trigger("click")

    // A tap has no hover to give, so it pins the card over instead.
    expect(button.attributes("aria-expanded")).toBe("true")
  })
})
