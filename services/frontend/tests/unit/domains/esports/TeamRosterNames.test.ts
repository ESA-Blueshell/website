import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import TeamRoster from "@/domains/esports/components/TeamRoster.vue"

vi.mock("@/plugins/require", () => ({$require: (path: string) => `resolved:${path}`}))
vi.mock("vuetify", () => ({useTheme: () => ({global: {current: {value: {dark: false}}}})}))

const mountRoster = (members: Array<{role: string; handle: string; name?: string}>) =>
  mount(TeamRoster, {
    props: {team: {id: 1, name: "BS Waterboarders", image: null, members} as never},
  })

describe("TeamRoster names", () => {
  it("names a member who allows it, beside their handle", () => {
    const wrapper = mountRoster([{role: "PLAYER", handle: "AriosFury", name: "Viktor Petrov"}])

    const row = wrapper.get(".team-roster__member")
    expect(row.text()).toContain("AriosFury")
    expect(row.get(".team-roster__name-aside").text()).toBe("Viktor Petrov")
  })

  it("shows the handle alone when no name was given", () => {
    const wrapper = mountRoster([{role: "PLAYER", handle: "Loafine"}])

    expect(wrapper.get(".team-roster__member").text()).toBe("Loafine")
    expect(wrapper.find(".team-roster__name-aside").exists()).toBe(false)
  })

  it("tells apart two members on the same roster", () => {
    const wrapper = mountRoster([
      {role: "PLAYER", handle: "AriosFury", name: "Viktor Petrov"},
      {role: "PLAYER", handle: "Loafine"},
    ])

    expect(wrapper.findAll(".team-roster__name-aside")).toHaveLength(1)
  })
})
