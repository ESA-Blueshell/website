import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import PlayedRosters from "@/domains/esports/island/PlayedRosters.vue"
import {settle} from "../../../pages/helpers"

const {mockLoad} = vi.hoisted(() => ({mockLoad: vi.fn()}))

vi.mock("@/domains/esports/adapters/esports", () => ({loadPlayedRosters: mockLoad}))
vi.mock("@/domains/esports/island/useGames", () => ({
  useGames: () => ({
    identityOf: (code: string) => ({
      name: code === "VALORANT" ? "Valorant" : "Counter-Strike 2",
      accent: "#fff",
      banner: null,
      icon: code === "VALORANT" ? "/valorant.webp" : null,
    }),
    recordOf: (code: string) => (code === "VALORANT" ? {slug: "valorant"} : null),
  }),
}))

const spot = (seasonId: number, game: string, team: string, extra: Record<string, unknown> = {}) =>
  ({game, seasonId, seasonName: `Season ${seasonId}`, seasonStart: "2025-09-01", teamId: seasonId * 10, teamName: team, role: "PLAYER", ...extra})

const open = async () => {
  const wrapper = mount(PlayedRosters, {props: {userId: 7}})
  await settle()
  return wrapper
}

describe("where somebody played", () => {
  it("groups roster spots by season, each opening that game's page on that season", async () => {
    mockLoad.mockResolvedValue([
      spot(2, "VALORANT", "Blue Shells", {roleTitle: "Captain"}),
      spot(2, "CS2", "Shell Shock", {role: "COACH"}),
      spot(1, "VALORANT", "Blue Shells"),
    ])
    const wrapper = await open()

    expect(mockLoad).toHaveBeenCalledWith(7)
    expect(wrapper.findAll("h3").map(one => one.text())).toEqual(["Season 2", "Season 1"])
    const rows = wrapper.findAll("[data-testid=played-roster]")
    expect(rows.map(row => row.find(".cut-row__title").text())).toEqual(["Blue Shells", "Shell Shock", "Blue Shells"])
    expect(rows[0]!.text()).toContain("Valorant · Captain")
    expect(rows[1]!.text()).toContain("Counter-Strike 2 · Coach")
    expect(rows[2]!.text()).toContain("Valorant · Player")
    expect(rows[0]!.attributes("to")).toBe("/esports/valorant?season=2")
    expect(rows[1]!.element.tagName).toBe("DIV")
    expect(rows[0]!.find("img").attributes("src")).toBe("/valorant.webp")
    expect(rows[1]!.text()).toContain("C")
    expect(wrapper.find("[data-testid=played-rosters-none]").exists()).toBe(false)
  })

  it("says so when no roster lists them yet", async () => {
    mockLoad.mockResolvedValue([])
    const wrapper = await open()

    expect(wrapper.get("[data-testid=played-rosters-none]").text()).toContain("No roster lists you yet")
    await wrapper.setProps({userId: 8})
    await settle()
    expect(mockLoad).toHaveBeenLastCalledWith(8)
  })
})
