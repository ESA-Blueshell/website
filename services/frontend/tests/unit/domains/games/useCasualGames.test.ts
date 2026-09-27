import {beforeEach, describe, expect, it, vi} from "vitest"
import {cellOf, driftItemOf, forgetCasualGames, initialsOf, reelItemOf, useCasualGames} from "@/domains/games"

const findCasualGames = vi.fn()
vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCasualGames: () => findCasualGames(),
}))

const picture = (path: string) => ({url: `/files/public/${path}`, path, width: 1600, height: 900, renditions: [{url: `/files/public/640/${path}`, width: 640}]})

const valorant = {
  code: "VALORANT", name: "Valorant", slug: "valorant", accent: "#ff4655", intro: "Stacks", sortIndex: 1, archived: false, inCompetition: true, channels: [{id: "6322", guildId: "324", name: "valorant"}, {id: "6323", guildId: "324", name: "hero-shooters"}],
  banner: picture("valorant.webp"), icon: picture("valorant-icon.webp"),
}
const dota = {code: "DOTA_2", name: "Dota 2", slug: "dota-2", accent: null, intro: null, sortIndex: 2, archived: true, inCompetition: false, banner: null, icon: null, channels: []}

beforeEach(() => {
  forgetCasualGames()
  findCasualGames.mockReset()
})

describe("the casual games", () => {
  it("reads the games once, resolves their pictures against the api, and splits the archived ones off", async () => {
    findCasualGames.mockResolvedValue({data: [valorant, dota]})
    const {games, live, archived, ready} = useCasualGames()
    await ready
    useCasualGames()

    expect(findCasualGames).toHaveBeenCalledTimes(1)
    expect(games.value[0].banner?.url).toBe("http://localhost:3000/api/files/public/valorant.webp")
    expect(games.value[0].banner?.renditions[0].url).toBe("http://localhost:3000/api/files/public/640/valorant.webp")
    expect(live.value.map(game => game.code)).toEqual(["VALORANT"])
    expect(archived.value.map(game => game.code)).toEqual(["DOTA_2"])
  })

  it("asks again when told to, and answers nothing where the api fails", async () => {
    findCasualGames.mockResolvedValueOnce({data: [valorant]}).mockResolvedValueOnce({error: {status: 500}})
    const {games, ready, refresh} = useCasualGames()
    await ready

    await refresh()

    expect(games.value).toEqual([])
  })
})

describe("a game on the reel", () => {
  it("carries its address, colour, art and icon", () => {
    expect(reelItemOf(valorant)).toEqual({
      id: "VALORANT",
      title: "Valorant",
      href: "/casual/valorant",
      accent: "#ff4655",
      banner: "/files/public/valorant.webp",
      srcset: "/files/public/640/valorant.webp 640w, /files/public/valorant.webp 1600w",
      icon: "/files/public/valorant-icon.webp",
      initials: "V",
      railLabel: "Valorant",
      notes: ["#valorant", "#hero-shooters"],
      chips: [],
    })
  })

  it("names its channels under the drift tile and the cell, and nothing where it has none", () => {
    expect(driftItemOf(valorant).sub).toBe("#valorant · #hero-shooters")
    expect(cellOf(valorant).sub).toBe("#valorant · #hero-shooters")
    expect(cellOf(dota)).toMatchObject({sub: undefined, archived: true})
    expect(cellOf(valorant, code => (code === "VALORANT" ? ["LanCie"] : [])).chips).toEqual(["LanCie"])
  })

  it("falls back to the association's blue and a plate where nothing was drawn", () => {
    expect(reelItemOf(dota)).toMatchObject({accent: "var(--color-brand)", banner: null, icon: null, initials: "D2"})
  })

  it("takes a plate's letters from the first two words, whatever the punctuation", () => {
    expect(initialsOf("Super Smash Bros.")).toBe("SS")
    expect(initialsOf("CS:GO")).toBe("C")
    expect(initialsOf("pokémon")).toBe("P")
  })
})
