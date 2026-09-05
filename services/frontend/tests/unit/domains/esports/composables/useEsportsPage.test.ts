import {beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, ref, type Ref} from "vue"
import {mount} from "@vue/test-utils"
import {useEsportsPage} from "@/domains/esports/composables/useEsportsPage"
import {forgetSeasons} from "@/domains/esports/island/useSeasons"
import {loadEsportsPage, loadSeasons, type EsportsPage} from "@/domains/esports/adapters/esports"
import {settle} from "../../../helpers/testUtils"

vi.mock("@/domains/esports/adapters/esports", () => ({
  loadEsportsPage: vi.fn(),
  loadSeasons: vi.fn(),
}))

const SPRING = {id: 19, name: "Spring 2025", startDate: "2025-02-01", endDate: "2025-08-31"}
const AUTUMN = {id: 20, name: "Autumn 2025", startDate: "2025-09-01", endDate: "2026-01-31"}

const pageAbout = (season: typeof SPRING): EsportsPage =>
  ({game: "VAL", season, seasons: [SPRING, AUTUMN], teams: []}) as never

/**
 * A fresh game code per reading, the composable holding one set of answers per game for the life
 * of the module: two tests sharing a code would share the first one's answers.
 */
let games = 0
const nextGame = () => `GAME_${++games}`

const asksAbout = (game: string, seasonId: number) =>
  vi.mocked(loadEsportsPage).mock.calls.filter(call => call[0] === game && call[1] === seasonId).length

const open = async (game: string, route: Ref<number | null>, onSeason = vi.fn()) => {
  let api!: ReturnType<typeof useEsportsPage>
  mount(defineComponent({
    setup() {
      api = useEsportsPage(game, () => route.value, onSeason)
      return () => h("div")
    },
  }))
  await settle()
  return {api, onSeason}
}

beforeEach(() => {
  vi.clearAllMocks()
  forgetSeasons()
  vi.mocked(loadSeasons).mockResolvedValue([SPRING, AUTUMN])
})

describe("useEsportsPage", () => {
  it("opens on the season the url names, with that season's teams", async () => {
    const game = nextGame()
    vi.mocked(loadEsportsPage).mockImplementation(async (_, id) => pageAbout(id === 19 ? SPRING : AUTUMN))

    const {api} = await open(game, ref<number | null>(19))

    expect(api.season.value?.id).toBe(19)
    expect(api.chosen.value).toBe(19)
  })

  it("opens on the association's newest season where the url names none", async () => {
    const game = nextGame()
    vi.mocked(loadEsportsPage).mockResolvedValue(pageAbout(AUTUMN))

    const {api} = await open(game, ref<number | null>(null))

    expect(api.chosen.value).toBe(20)
  })

  // The api chose the season, so the answer is written down under it: the panel standing on that
  // season is the one that has to be able to find it.
  it("writes an answer read without naming a season down under the season it turned out to be about", async () => {
    const game = nextGame()
    vi.mocked(loadSeasons).mockResolvedValue([])
    vi.mocked(loadEsportsPage).mockResolvedValue(pageAbout(AUTUMN))

    const {api} = await open(game, ref<number | null>(null))

    expect(vi.mocked(loadEsportsPage).mock.calls[0]?.[1]).toBeUndefined()
    expect(api.answerFor(20)).not.toBeUndefined()
  })

  /**
   * The behaviour `esports-mobile.spec.ts` proves through a gesture: a read the api would not make
   * comes back as a body about another season rather than as an error, so the page has an answer
   * it cannot use. Held under the season asked for, one refusal would be the last word on that
   * season for the life of the page.
   */
  it("asks the api again for a season it answered about another season", async () => {
    const game = nextGame()
    vi.mocked(loadEsportsPage).mockResolvedValue(pageAbout(AUTUMN))

    const {api} = await open(game, ref<number | null>(19))
    await api.showSeason(19)
    await settle()

    expect(asksAbout(game, 19)).toBe(2)
  })

  it("answers from what it holds for a season the api did answer about", async () => {
    const game = nextGame()
    vi.mocked(loadEsportsPage).mockImplementation(async (_, id) => pageAbout(id === 19 ? SPRING : AUTUMN))

    const {api} = await open(game, ref<number | null>(19))
    await api.showSeason(20)
    await api.showSeason(19)
    await settle()

    expect(asksAbout(game, 19)).toBe(1)
  })

  // The strip and the address bar are about what the visitor asked for rather than what the api
  // managed to answer; skipping this strands the url on a season that never arrived.
  it("writes the url for the season asked for, even where the answer is about another", async () => {
    const game = nextGame()
    vi.mocked(loadEsportsPage).mockResolvedValue(pageAbout(AUTUMN))

    const {api, onSeason} = await open(game, ref<number | null>(20))
    await api.showSeason(19)

    expect(onSeason).toHaveBeenCalledWith(19)
  })

  it("declines to re-read the season already shown, which is what reload is for", async () => {
    const game = nextGame()
    vi.mocked(loadEsportsPage).mockImplementation(async (_, id) => pageAbout(id === 19 ? SPRING : AUTUMN))

    const {api} = await open(game, ref<number | null>(19))
    await api.showSeason(19)
    expect(asksAbout(game, 19)).toBe(1)

    await api.reload(19)

    expect(asksAbout(game, 19)).toBe(2)
  })

  // A season chosen elsewhere — the back button, a shared link — is still a season change.
  it("follows a season the url changes to", async () => {
    const game = nextGame()
    vi.mocked(loadEsportsPage).mockImplementation(async (_, id) => pageAbout(id === 19 ? SPRING : AUTUMN))
    const route = ref<number | null>(19)

    const {api} = await open(game, route)
    route.value = 20
    await settle()

    expect(api.season.value?.id).toBe(20)
  })

  // A season nobody has asked about is still loading; a season the game sat out is an answer.
  it("says nothing at all for a season nobody has asked about", async () => {
    const game = nextGame()
    vi.mocked(loadEsportsPage).mockImplementation(async (_, id) => pageAbout(id === 19 ? SPRING : AUTUMN))

    const {api} = await open(game, ref<number | null>(19))

    expect(api.answerFor(20)).toBeUndefined()

    api.askAhead(20)
    await settle()

    expect(api.answerFor(20)).not.toBeUndefined()
  })
})
