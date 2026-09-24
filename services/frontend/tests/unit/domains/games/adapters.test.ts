import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  addCasualGame,
  loadGameHoldings,
  removeCasualGame,
  saveCasualGame,
  setGameArchived,
  storeGamePicture,
} from "@/domains/games/adapters/games"
import {sentenceFor} from "@/domains/games/refusals"

const api = vi.hoisted(() => ({
  createCasualGame: vi.fn(),
  updateCasualGame: vi.fn(),
  archiveGame: vi.fn(),
  findGameHoldings: vi.fn(),
  removeGame: vi.fn(),
  uploadPublicImage: vi.fn(),
}))
vi.mock("@/services/api", async importOriginal => ({...(await importOriginal<typeof import("@/services/api")>()), ...api}))

const game = {code: "CHESS", name: "Chess", slug: "chess", accent: null, intro: null, sortIndex: 1, archived: false, inCompetition: false,
  banner: {url: "/files/public/b.webp", path: "b.webp", renditions: []}, icon: null}
const draft = {name: "Chess", slug: "chess", intro: null, accent: "#b58863", banner: null, icon: "i.webp"}
const refused = {error: {code: "AddressTaken", gameName: "Go", address: "chess"}}

beforeEach(() => Object.values(api).forEach(one => one.mockReset()))

describe("the games adapter", () => {
  it("adds and saves a game, resolving its pictures, and says why a write was refused", async () => {
    api.createCasualGame.mockResolvedValueOnce({data: game}).mockResolvedValueOnce(refused)
    api.updateCasualGame.mockResolvedValueOnce({data: game}).mockResolvedValueOnce({error: {}})

    expect(await addCasualGame(draft)).toMatchObject({ok: true, game: {banner: {url: "http://localhost:3000/api/files/public/b.webp"}}})
    expect(api.createCasualGame).toHaveBeenCalledWith({body: {name: "Chess", slug: "chess", intro: undefined, accent: "#b58863", banner: undefined, icon: "i.webp"}})
    expect(await addCasualGame(draft)).toEqual({ok: false, reason: "The address 'chess' is already used by Go."})
    expect(await saveCasualGame("CHESS", draft)).toMatchObject({ok: true})
    expect(await saveCasualGame("CHESS", draft)).toEqual({ok: false, reason: "The game could not be saved."})
  })

  it("archives, brings back and removes a game, and reads what a removal would touch", async () => {
    api.archiveGame.mockResolvedValueOnce({data: {...game, archived: true}}).mockResolvedValueOnce({error: {}}).mockResolvedValueOnce({error: {}})
    api.findGameHoldings.mockResolvedValueOnce({data: {channels: 1, committees: 0, events: 2, teams: 0, people: 0}}).mockResolvedValueOnce({})
    api.removeGame.mockResolvedValueOnce({}).mockResolvedValueOnce({error: {code: "GameNotArchived", gameName: "Chess"}})

    expect(await setGameArchived("CHESS", true)).toMatchObject({ok: true, game: {archived: true}})
    expect(await setGameArchived("CHESS", true)).toEqual({ok: false, reason: "The game could not be archived."})
    expect(await setGameArchived("CHESS", false)).toEqual({ok: false, reason: "The game could not be brought back."})
    expect(await loadGameHoldings("CHESS")).toEqual({channels: 1, committees: 0, events: 2, teams: 0, people: 0})
    expect(await loadGameHoldings("CHESS")).toBeNull()
    expect(await removeCasualGame("CHESS")).toEqual({ok: true})
    expect(await removeCasualGame("CHESS")).toEqual({ok: false, reason: "Chess is still played. Archive it first, then it can be removed."})
  })

  it("stores a chosen picture as the kind it is, or says why it could not", async () => {
    const file = new File(["x"], "b.png")
    api.uploadPublicImage.mockResolvedValueOnce({data: {url: "/files/public/b.webp", path: "b.webp", renditions: []}}).mockResolvedValueOnce({error: {code: "PictureNotStored"}})

    expect(await storeGamePicture(file, "GAME_BANNER")).toMatchObject({ok: true, picture: {path: "b.webp"}})
    expect(api.uploadPublicImage).toHaveBeenCalledWith({query: {type: "GAME_BANNER"}, body: {file}})
    expect(await storeGamePicture(file, "GAME_ICON")).toEqual({ok: false, reason: "That picture is not in storage."})
  })
})

describe("the games' refusals", () => {
  it("says each refusal in a sentence of its own", () => {
    expect(sentenceFor({code: "UnknownGameCode", gameCode: "PONG"})).toBe("There is no game with the code 'PONG'.")
    expect(sentenceFor({code: "GameNameBlank"})).toBe("A game needs a name.")
    expect(sentenceFor({code: "GameNameUnusable", given: "!!"})).toBe("'!!' has no letters or digits to make a code from.")
    expect(sentenceFor({code: "GameAlreadyExists", gameName: "Chess"})).toBe("Chess is already a game.")
    expect(sentenceFor({code: "GameAddressBlank"})).toBe("A game's page needs an address.")
    expect(sentenceFor({code: "AddressReserved", address: "competitive-scene"})).toBe("The address 'competitive-scene' belongs to the competition index.")
    expect(sentenceFor({code: "GameHoldsHistory", gameName: "CS:GO", teams: 1, players: 5}))
      .toBe("CS:GO holds 1 team and 5 people in competition, so it cannot be removed. It stays archived, and everything it played stays readable.")
    expect(sentenceFor({code: "GameHoldsHistory"})).toContain("That game holds 0 teams and 0 people")
  })
})
