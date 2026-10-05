import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import TeamList from "@/pages/management/TeamList.vue"
import {forgetCasualGames} from "@/domains/games"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({findCasualGames: vi.fn(), findTeams: vi.fn(), findTeamSeasons: vi.fn(), findFieldings: vi.fn(), findCohorts: vi.fn(), listCataloguedChannels: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const season = (id: number, name: string, startDate: string) => ({id, name, startDate, endDate: "2099-01-01", played: true})
const game = (code: string, name: string) => ({code, name, slug: name.toLowerCase(), archived: false, inCompetition: true, sortIndex: 0, channels: [], esportsChannels: []})

describe("the competition teams in Management", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(TeamList)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    forgetCasualGames()
    api.findCasualGames.mockResolvedValue({status: 200, data: [game("VAL", "Valorant"), game("CS2", "Counter")]})
    api.findTeams.mockResolvedValue({status: 200, data: [
      {id: 1, name: "Blueshell Valorant", archived: false},
      {id: 2, name: "Blueshell CS", archived: false},
      {id: 3, name: "Old team", archived: true},
    ]})
    api.findFieldings.mockResolvedValue({status: 200, data: [
      {teamId: 1, game: "VAL", season: season(3, "Autumn 2025", "2025-09-01")},
      {teamId: 1, game: "VAL", season: season(4, "Spring 2026", "2026-02-01")},
      {teamId: 2, game: "LOL", season: season(4, "Spring 2026", "2026-02-01")},
    ]})
    api.findCohorts.mockResolvedValue({status: 200, data: [
      {id: 9, type: "TEAM_PLAYERS", category: "TEAMS", label: "x", memberCount: 0, mappingCount: 1, definitionKey: "TEAM_PLAYERS:1",
        targets: [{system: "DISCORD", label: "Blueshell Valorant", made: true, externalId: "960"}]},
    ]})
    api.listCataloguedChannels.mockResolvedValue({status: 200, data: [
      {id: "1", name: "bs-valorant", kind: "TEXT", private: true, roleIds: ["960"]},
      {id: "2", name: "Esports", kind: "CATEGORY", private: true, roleIds: ["960"]},
      {id: "3", name: "general", kind: "TEXT", private: false, roleIds: []},
    ]})
    api.findTeamSeasons.mockImplementation(({path}: {path: {teamId: number}}) => Promise.resolve({status: 200, data: ({
      1: [{game: "VAL", season: season(3, "Autumn 2025", "2025-09-01")}, {game: "VAL", season: season(4, "Spring 2026", "2026-02-01")}],
      2: [{game: "LOL", season: season(4, "Spring 2026", "2026-02-01")}],
      3: [],
    } as Record<number, unknown[]>)[path.teamId]}))
  })

  afterEach(() => unmountAll(wrappers, "TeamListPage"))

  it("lists each team with its games, latest season, role and channels, opening the line-up editor on that fielding", async () => {
    const wrapper = await mount()

    const valorant = wrapper.get('[data-testid="team-row-1"]')
    expect(valorant.text()).toContain("Valorant")
    expect(valorant.text()).toContain("Spring 2026")
    expect(valorant.get("a").attributes("to")).toBe("/management/competition/valorant/teams/1?season=4")
    expect(wrapper.get('[data-testid="team-row-2"]').text()).toContain("LOL")
    expect(wrapper.get('[data-testid="team-row-2"]').find("a").exists()).toBe(false)
    expect(wrapper.get('[data-testid="team-row-3"]').text()).toContain("Never fielded")
    expect(wrapper.get('[data-testid="team-row-3"]').text()).toContain("Archived")
    expect(wrapper.findAll('[data-testid^="team-row-"]').at(-1)!.attributes("data-testid")).toBe("team-row-3")
    expect(wrapper.findComponent({name: "FactList"}).text()).toContain("1 archived")
    expect(wrapper.get("thead").text()).toContain("Latest season")
    expect(wrapper.get('[data-testid="team-discord-1"]').attributes("to")).toBe("/management/platforms/discord/roles/960")
    expect(wrapper.get('[data-testid="team-channels-1"]').text()).toBe("#bs-valorant")
    expect(wrapper.get('[data-testid="team-discord-2"]').text()).toBe("No role")
    expect(wrapper.get('[data-testid="team-channels-2"]').text()).toBe("No channel")
    // One read for every team, not one a team.
    expect(api.findTeamSeasons).not.toHaveBeenCalled()
  })

  it("asks each team for its own seasons where the one read gives no answer, and shows no team before they are known", async () => {
    api.findFieldings.mockResolvedValue({status: 500, error: {}})
    let answer: (() => void) | undefined
    const held = new Promise<void>((resolve) => { answer = resolve })
    const seasons = api.findTeamSeasons.getMockImplementation()!
    api.findTeamSeasons.mockImplementation(async (options: {path: {teamId: number}}) => {
      await held
      return seasons(options)
    })
    const wrapper = await mount()

    expect(wrapper.findAll('[data-testid^="team-row-"]')).toHaveLength(0)
    expect(wrapper.text()).not.toContain("Never fielded Blueshell")
    answer!()
    await settle()

    expect(api.findTeamSeasons).toHaveBeenCalledTimes(3)
    expect(wrapper.get('[data-testid="team-row-1"] a').attributes("to")).toBe("/management/competition/valorant/teams/1?season=4")
    expect(wrapper.get('[data-testid="team-row-3"]').text()).toContain("Never fielded")
  })

  it("draws each team as a row on a phone, one never fielded opening nothing", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = await mount()
    vi.unstubAllGlobals()

    expect(wrapper.get('[data-testid="team-row-1-open"]').attributes("to")).toBe("/management/competition/valorant/teams/1?season=4")
    expect(wrapper.find('[data-testid="team-row-3-open"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="team-row-3"]').text()).toContain("Never fielded")
  })

  it("narrows by a team or a game, and says when nothing matches", async () => {
    const wrapper = await mount()

    wrapper.findComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "valorant")
    await settle()
    expect(wrapper.findAll('[data-testid^="team-row-"]').map((one) => one.attributes("data-testid"))).toEqual(["team-row-1"])
    wrapper.findComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "zzz")
    await settle()
    expect(wrapper.get('[data-testid="team-list-empty"]').text()).toBe("No team matches.")
  })
})
