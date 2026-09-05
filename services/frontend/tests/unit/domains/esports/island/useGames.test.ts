import {beforeEach, describe, expect, it, vi} from "vitest"
import {forgetGames, useGames} from "@/domains/esports/island/useGames"
import {loadGames, type Game} from "@/domains/esports/adapters/esports"

vi.mock("@/domains/esports/adapters/esports", () => ({
  loadGames: vi.fn(),
}))

const art = (path: string) => ({
  path,
  url: path,
  width: 1200,
  height: 400,
  renditions: [{url: `${path}?w=600`, width: 600}],
})

const VALORANT = {
  code: "VAL", name: "Valorant", slug: "valorant", current: true,
  accent: "#ff4655", banner: art("/media/val.png"), icon: art("/media/val-icon.png"),
} as unknown as Game

const CSGO = {
  code: "CSGO", name: "CS:GO", slug: "counter-strike-global-offensive", current: false,
  accent: null, banner: null, icon: null,
} as unknown as Game

const read = async () => {
  const games = useGames()
  await games.ready
  return games
}

beforeEach(() => {
  vi.clearAllMocks()
  forgetGames()
  vi.mocked(loadGames).mockResolvedValue([VALORANT, CSGO])
})

describe("useGames", () => {
  it("draws a game with the name, accent and art its record carries", async () => {
    const games = await read()

    expect(games.identityOf("VAL")).toMatchObject({
      name: "Valorant",
      accent: "#ff4655",
      icon: "/media/val-icon.png",
      banner: "/media/val.png",
      width: 1200,
      height: 400,
    })
    expect(games.identityOf("VAL").srcset).toContain("/media/val.png?w=600 600w")
  })

  it("draws a game nobody has chosen art for on the association's own blue, with no icon", async () => {
    const games = await read()

    expect(games.identityOf("CSGO")).toMatchObject({
      name: "CS:GO", accent: "var(--color-brand)", icon: null, banner: null,
    })
  })

  // No record means the records have not answered yet, or the code names no game. Either way
  // there is no name to print: the raw code is not one, and showing it flashes.
  it("prints no name at all for a code that names no game", async () => {
    const games = await read()

    expect(games.identityOf("NOPE")).toMatchObject({name: "", accent: "var(--color-brand)"})
    expect(games.recordOf("NOPE")).toBeNull()
  })

  // Currently played is the api's answer, derived from what was fielded, never a stored flag.
  it("offers the games the association plays now as the ones its records say are current", async () => {
    const games = await read()

    expect(games.current.value.map(one => one.code)).toEqual(["VAL"])
  })

  it("finds a game by the address its page answers to", async () => {
    const games = await read()

    expect(games.bySlug("valorant")?.code).toBe("VAL")
    expect(games.bySlug("no-such-page")).toBeNull()
  })

  it("reads the games once, however many pages ask for them", async () => {
    await read()
    await read()

    expect(vi.mocked(loadGames).mock.calls.length).toBe(1)
  })

  it("reads them again once a page that wrote a game says to forget", async () => {
    await read()
    forgetGames()
    await read()

    expect(vi.mocked(loadGames).mock.calls.length).toBe(2)
  })
})
