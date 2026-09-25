import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {computed, ref} from "vue"
import TeamEdit from "@/pages/competition/TeamEdit.vue"

const route = vi.hoisted(() => ({params: {} as Record<string, string>, query: {} as Record<string, string>}))
const router = vi.hoisted(() => ({replace: vi.fn(), options: {history: {state: {back: null}}}}))
vi.mock("vue-router", async importOriginal => ({...(await importOriginal<typeof import("vue-router")>()), useRoute: () => route, useRouter: () => router}))
const autumn = {id: 3, name: "Autumn 2025"}
const spring = {id: 4, name: "Spring 2026"}
const valorant = {code: "VAL", name: "Valorant", slug: "valorant", accent: null}
const read = vi.hoisted(() => ({useTeamToEdit: vi.fn()}))
vi.mock("@/domains/esports", () => ({
  useGames: () => ({ready: Promise.resolve([]), bySlug: (slug: string) => (slug === "valorant" ? valorant : null)}),
  useSeasons: () => ({seasons: ref([autumn, spring]), ready: Promise.resolve([]), newest: computed(() => spring)}),
  useTeamToEdit: read.useTeamToEdit,
}))

const TeamEditor = {
  name: "TeamEditor",
  props: ["game", "gameName", "season", "teamId", "teamName", "teamBanner", "teamIcon", "alreadyFielded", "back", "accent"],
  emits: ["saved", "removed", "cancel"],
  template: "<div />",
}
const stubs = {TeamEditor, NotFound: {template: "<div data-testid=missing />"}}

const answer = (season: unknown, teams: {id: number, name: string}[], team: unknown) => ({
  season: ref(season), fielded: ref(teams), team: ref(team), answered: Promise.resolve(),
})

const mountPage = async (params: Record<string, string>, query: Record<string, string> = {}) => {
  route.params = params
  route.query = query
  const wrapper = mount(TeamEdit, {global: {stubs}})
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  router.replace.mockReset()
  read.useTeamToEdit.mockReset()
})

describe("the team edit page", () => {
  it("corrects a team's line-up in the season asked for, and goes back to the game page on it", async () => {
    const team = {id: 9, name: "Blueshell", banner: null, icon: null}
    read.useTeamToEdit.mockReturnValue(answer(autumn, [team, {id: 10, name: "Two"}], team))
    const wrapper = await mountPage({slug: "valorant", team: "9"}, {season: "3"})
    const editor = wrapper.getComponent(TeamEditor)

    expect(read.useTeamToEdit).toHaveBeenCalledWith("VAL", 9, 3)
    expect(editor.props()).toMatchObject({
      game: "VAL", gameName: "Valorant", season: autumn, teamId: 9, teamName: "Blueshell", alreadyFielded: [9, 10],
      back: "/competition/valorant?season=3", accent: "var(--color-brand)",
    })
    editor.vm.$emit("saved")
    editor.vm.$emit("removed")
    editor.vm.$emit("cancel")
    expect(router.replace.mock.calls).toEqual(Array(3).fill(["/competition/valorant?season=3"]))
  })

  it("adds a team in a season the game has not played, or the newest where none is asked for", async () => {
    read.useTeamToEdit.mockReturnValue(answer(null, [], null))
    const asked = await mountPage({slug: "valorant"}, {season: "3"})
    expect(asked.getComponent(TeamEditor).props()).toMatchObject({season: autumn, teamId: null, teamName: ""})

    const newest = await mountPage({slug: "valorant"})
    expect(newest.getComponent(TeamEditor).props()).toMatchObject({season: spring, back: "/competition/valorant"})
  })

  it("reads a game or a team nobody answers to as not found", async () => {
    read.useTeamToEdit.mockReturnValue(answer(autumn, [], null))

    expect((await mountPage({slug: "pong"})).find("[data-testid=missing]").exists()).toBe(true)
    expect((await mountPage({slug: "valorant", team: "99"})).find("[data-testid=missing]").exists()).toBe(true)
  })
})
