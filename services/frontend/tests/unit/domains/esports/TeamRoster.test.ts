import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import TeamRoster from "@/domains/esports/components/TeamRoster.vue"

vi.mock("@/plugins/require", () => ({$require: (path: string) => `resolved:${path}`}))
vi.mock("vuetify", () => ({useTheme: () => ({global: {current: {value: {dark: false}}}})}))

const team = (members: Array<{role: string; handle: string}>) => ({
  id: 7,
  name: "BS Waterboarders",
  image: "valorantesports1.jpg",
  members,
})

const mountRoster = (members: Array<{role: string; handle: string}>) =>
  mount(TeamRoster, {props: {team: team(members) as never}})

describe("TeamRoster", () => {
  it("groups a roster into players, substitutes and coaches, in that order", () => {
    const wrapper = mountRoster([
      {role: "COACH", handle: "coachy"},
      {role: "PLAYER", handle: "one"},
      {role: "SUBSTITUTE", handle: "benched"},
      {role: "PLAYER", handle: "two"},
    ])

    const labels = wrapper.findAll(".team-roster__group-label").map((node) => node.text())
    expect(labels).toEqual(["Players", "Substitute", "Coach"])
  })

  it("names a group in the singular when it holds one person", () => {
    const wrapper = mountRoster([{role: "PLAYER", handle: "solo"}])

    expect(wrapper.get(".team-roster__group-label").text()).toBe("Player")
  })

  it("leaves out a group nobody is in", () => {
    const wrapper = mountRoster([{role: "PLAYER", handle: "one"}])

    expect(wrapper.findAll(".team-roster__group")).toHaveLength(1)
  })

  it("shows the handle and never a name, because it is given none", () => {
    const wrapper = mountRoster([{role: "PLAYER", handle: "AriosFury"}])

    expect(wrapper.get(".team-roster__member").text()).toBe("AriosFury")
  })

  it("resolves the team's background out of the asset bundle", () => {
    const wrapper = mountRoster([{role: "PLAYER", handle: "one"}])

    expect(wrapper.get(".team-roster").attributes("style"))
      .toContain("resolved:@/assets/valorantesports1.jpg")
  })
})
