import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  addToRoster,
  dropSeasonOrReason,
  dropTeam,
  enterGameInSeason,
  fieldTeamInSeason,
  leaveGameInSeason,
  loadEsportsPage,
  loadGameAccounts,
  loadPlayedRosters,
  loadGames,
  loadSeasonContents,
  loadSeasonGames,
  loadTeams,
  saveSeasonOrReason,
  storePicture,
  unfieldTeamFromSeason,
} from "@/domains/esports/adapters/esports"
import {
  addRosterEntry,
  apiUrl,
  createSeason,
  deleteSeason,
  deleteTeam,
  enterGame,
  fieldTeam,
  findGame,
  findGameAccounts,
  findCasualGames,
  findPlayedRosters,
  findSeasonContents,
  findSeasonGames,
  findTeams,
  leaveGame,
  unfieldTeam,
  uploadPublicImage,
} from "@/services/api"
import type {Image} from "@/services/api"
import {FileType, TeamRole, type CasualGameResponse} from "@/services/api"
import {aGame, aSeason, aTeam} from "../../../helpers/apiFixtures"
import {answer, emptyAnswer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  addRosterEntry: vi.fn(),
  createSeason: vi.fn(),
  deleteSeason: vi.fn(),
  deleteTeam: vi.fn(),
  enterGame: vi.fn(),
  fieldTeam: vi.fn(),
  findGame: vi.fn(),
  findGameAccounts: vi.fn(),
  findPlayedRosters: vi.fn(),
  findCasualGames: vi.fn(),
  findSeasonContents: vi.fn(),
  findSeasonGames: vi.fn(),
  findTeams: vi.fn(),
  leaveGame: vi.fn(),
  unfieldTeam: vi.fn(),
  uploadPublicImage: vi.fn(),
}))

/** An image as the api answers with one: paths of its own, which the adapter has to resolve. */
const picture = (path: string): Image => ({
  path,
  url: path,
  width: 1200,
  height: 400,
  renditions: [{url: `${path}?w=600`, width: 600}],
})

const optionsOf = (call: unknown) => call as Record<string, any>

beforeEach(() => {
  vi.clearAllMocks()
})

describe("loadGames", () => {
  it("answers with the games it read, drawn against the api rather than the page's own origin", async () => {
    vi.mocked(findCasualGames).mockResolvedValue(answer(findCasualGames, [aGame({code: "VAL", name: "Valorant", banner: picture("/media/val.png"), icon: null})]))

    const [game] = await loadGames()

    expect(game?.banner?.url).toBe(apiUrl("/media/val.png"))
    expect(game?.banner?.renditions[0]?.url).toBe(apiUrl("/media/val.png?w=600"))
  })

  // Every page asks for this, including ones served before the api is reachable, so a body that
  // is not the list it was promised reads as no games rather than taking the navigation down.
  it("answers with no games at all where the body was not a list", async () => {
    vi.mocked(findCasualGames).mockResolvedValue(answer(findCasualGames, {message: "no"} as unknown as CasualGameResponse[]))

    await expect(loadGames()).resolves.toEqual([])
  })
})




describe("loadSeasonContents", () => {
  // Deliberate rather than an oversight: this answers zero where it could not read, so the offer
  // to remove says the season is empty.
  it("answers that the season holds nothing where the read failed", async () => {
    vi.mocked(findSeasonContents).mockResolvedValue(refusal(findSeasonContents, {status: 500}))

    await expect(loadSeasonContents(19)).resolves.toEqual({teams: 0, players: 0})
  })
})

describe("storePicture", () => {
  it("answers with the whole image, every width of it drawn against the api", async () => {
    vi.mocked(uploadPublicImage).mockResolvedValue(answer(uploadPublicImage, picture("/media/banner.png")))

    const stored = await storePicture(new File([], "banner.png"), FileType.GAME_BANNER)

    expect(stored).toMatchObject({ok: true})
    expect((stored as {picture: {url: string}}).picture.url).toBe(apiUrl("/media/banner.png"))
  })

  // "Something went wrong" does not tell somebody to pick another file.
  it("answers a refused upload in the api's own words", async () => {
    vi.mocked(uploadPublicImage).mockResolvedValue(refusal(uploadPublicImage, {detail: "That file is not an image."}))

    await expect(storePicture(new File([], "notes.txt"), FileType.GAME_BANNER))
      .resolves.toEqual({ok: false, reason: "That file is not an image."})
  })
})

describe("loadEsportsPage", () => {
  it("draws every team's art, and every person's, against the api", async () => {
    vi.mocked(findGame).mockResolvedValue(answer(findGame, {
        game: "VAL",
        season: aSeason({id: 20}),
        seasons: [],
        teams: [{
          id: 1, name: "BS Waterboarders", banner: picture("/media/team.png"), icon: null,
          members: [{handle: "nova", role: TeamRole.PLAYER, icon: picture("/media/nova.png")}],
        }],
      }))

    const page = await loadEsportsPage("VAL", 20)

    expect(page?.teams[0]?.banner?.url).toBe(apiUrl("/media/team.png"))
    expect(page?.teams[0]?.members[0]?.icon?.url).toBe(apiUrl("/media/nova.png"))
  })

  it("asks about no season in particular where none was named, the api choosing one", async () => {
    vi.mocked(findGame).mockResolvedValue(answer(findGame, {game: "VAL", season: aSeason({id: 20}), seasons: [], teams: []}))

    await loadEsportsPage("VAL")

    expect(optionsOf(vi.mocked(findGame).mock.calls[0]?.[0]).query).toEqual({})
  })

  it("answers with nothing at all where there was no page", async () => {
    vi.mocked(findGame).mockResolvedValue(refusal(findGame, {status: 404}))

    await expect(loadEsportsPage("VAL", 20)).resolves.toBeNull()
  })
})

describe("saveSeasonOrReason", () => {
  it("writes a season that has no id yet, and corrects one that has", async () => {
    vi.mocked(createSeason).mockResolvedValue(answer(createSeason, aSeason({id: 21})))

    await expect(saveSeasonOrReason(undefined, {name: "Autumn 2025", startDate: "2025-09-01", endDate: "2026-01-31"}))
      .resolves.toEqual({ok: true, saved: aSeason({id: 21})})

    expect(createSeason).toHaveBeenCalled()
  })

  it("answers with the api's own account of dates that overlap another season", async () => {
    vi.mocked(createSeason).mockResolvedValue(refusal(createSeason, {code: "SeasonDatesOverlap", seasonName: "Spring 2025"}))

    await expect(saveSeasonOrReason(undefined, {name: "Autumn 2025", startDate: "2025-01-01", endDate: "2026-01-31"}))
      .resolves.toEqual({ok: false, reason: "Those dates overlap Spring 2025."})
  })

  it("counts an answer carrying no season as a refusal, not as a write that landed", async () => {
    vi.mocked(createSeason).mockResolvedValue(emptyAnswer(createSeason))

    await expect(saveSeasonOrReason(undefined, {name: "Autumn 2025", startDate: "2025-09-01", endDate: "2026-01-31"}))
      .resolves.toEqual({ok: false, reason: "The season could not be saved."})
  })
})

describe("loadSeasonGames", () => {
  it("answers with each game the season ran, and whether a visitor sees it", async () => {
    vi.mocked(findSeasonGames).mockResolvedValue(answer(findSeasonGames, [{game: "VAL", public: false, teams: [{id: 1, name: "BS Waterboarders", members: []}]}]))

    await expect(loadSeasonGames(20)).resolves.toMatchObject([{game: "VAL", public: false}])
  })

  it("answers with no games where the read failed", async () => {
    vi.mocked(findSeasonGames).mockResolvedValue(refusal(findSeasonGames, {status: 500}))

    await expect(loadSeasonGames(20)).resolves.toEqual([])
  })
})

describe("enterGameInSeason", () => {
  it("answers with the game entered, holding nobody until a team is fielded in it", async () => {
    vi.mocked(enterGame).mockResolvedValue(answer(enterGame, {game: "VAL", public: false, teams: []}))

    await expect(enterGameInSeason(20, "VAL"))
      .resolves.toEqual({ok: true, saved: {game: "VAL", teams: [], public: false}})
  })

  it("answers with the api's account of why the entry was refused", async () => {
    vi.mocked(enterGame).mockResolvedValue(refusal(enterGame, {code: "GameFieldedInSeason", gameName: "Valorant", teams: 2}))

    const answer = await enterGameInSeason(20, "VAL")

    expect(answer.ok).toBe(false)
    expect((answer as {reason: string}).reason).toContain("Valorant still has 2 teams")
  })

  it("counts an answer carrying no game as a refusal, not as an entry that landed", async () => {
    vi.mocked(enterGame).mockResolvedValue(emptyAnswer(enterGame))

    await expect(enterGameInSeason(20, "VAL"))
      .resolves.toEqual({ok: false, reason: "That game could not be put into the season."})
  })
})

describe("leaveGameInSeason", () => {
  it("answers with the api's account of the teams still in the season", async () => {
    vi.mocked(leaveGame).mockResolvedValue(refusal(leaveGame, {code: "GameFieldedInSeason", gameName: "Valorant", teams: 2}))

    const answer = await leaveGameInSeason(20, "VAL")

    expect((answer as {reason: string}).reason).toContain("Valorant still has 2 teams")
  })
})

describe("fieldTeamInSeason", () => {
  it("brings the line-up across from the fielding that was chosen", async () => {
    vi.mocked(fieldTeam).mockResolvedValue(answer(fieldTeam, {team: aTeam({id: 7}), game: "VAL", season: aSeason({id: 20}), carried: []}))

    await fieldTeamInSeason(7, 20, {game: "VAL", carryLineup: true, carryFrom: {game: "CS2", seasonId: 19}})

    expect(optionsOf(vi.mocked(fieldTeam).mock.calls[0]?.[0]).body)
      .toMatchObject({game: "VAL", carryLineup: true, carryFrom: {game: "CS2", seasonId: 19}})
  })

  // Naming no banner leaves the art alone: a team is re-fielded to say it plays this season as
  // often as to change its picture.
  it("says nothing about the banner where none was named, rather than taking it away", async () => {
    vi.mocked(fieldTeam).mockResolvedValue(answer(fieldTeam, {team: aTeam({id: 7}), game: "VAL", season: aSeason({id: 20}), carried: []}))

    await fieldTeamInSeason(7, 20, {game: "VAL", carryLineup: false})

    expect(optionsOf(vi.mocked(fieldTeam).mock.calls[0]?.[0]).body.banner).toBeUndefined()
  })

  // The body is what says the fielding happened; the roster writes that follow would otherwise
  // land on a fielding nobody confirmed.
  it("refuses a fielding the api answered with nothing at all", async () => {
    vi.mocked(fieldTeam).mockResolvedValue(emptyAnswer(fieldTeam))

    await expect(fieldTeamInSeason(7, 20, {game: "VAL", carryLineup: false}))
      .resolves.toEqual({ok: false, reason: "That team could not be fielded this season."})
  })
})

describe("addToRoster", () => {
  it("refuses an entry the api answered with nothing, so nobody is reported as put on", async () => {
    vi.mocked(addRosterEntry).mockResolvedValue(emptyAnswer(addRosterEntry))

    await expect(addToRoster(7, {game: "VAL", seasonId: 20, handle: "nova", role: TeamRole.PLAYER}))
      .resolves.toEqual({ok: false, reason: "That person could not be put on the roster."})
  })
})

describe("loadTeams", () => {
  it("answers with no teams where the read failed, the pool being shared and read on every page", async () => {
    vi.mocked(findTeams).mockResolvedValue(refusal(findTeams, {status: 500}))

    await expect(loadTeams()).resolves.toEqual([])
  })
})

describe("loadPlayedRosters", () => {
  const played = {
    teamId: 1, teamName: "Blue Shells", game: "VAL", role: TeamRole.PLAYER,
    seasonId: 19, seasonName: "Season 19", seasonStart: "2026-09-01",
  }

  it("answers with the roster spots a person held, or none where the read failed", async () => {
    vi.mocked(findPlayedRosters).mockResolvedValueOnce(answer(findPlayedRosters, [played]))
    await expect(loadPlayedRosters(5)).resolves.toEqual([played])
    expect(findPlayedRosters).toHaveBeenCalledWith({path: {userId: 5}})

    vi.mocked(findPlayedRosters).mockResolvedValueOnce(refusal(findPlayedRosters, {status: 500}))
    await expect(loadPlayedRosters(5)).resolves.toEqual([])
  })
})

describe("loadGameAccounts", () => {
  it("answers with no handles where the read failed", async () => {
    vi.mocked(findGameAccounts).mockResolvedValue(refusal(findGameAccounts, {status: 500}))

    await expect(loadGameAccounts(5)).resolves.toEqual([])
  })
})


describe("the removals", () => {
  it("drops a team from a season, a season and a team, each refusal in its own words", async () => {
    vi.mocked(unfieldTeam).mockResolvedValueOnce(answer(unfieldTeam, undefined)).mockResolvedValueOnce(refusal(unfieldTeam, {}))
    vi.mocked(deleteSeason).mockResolvedValueOnce(answer(deleteSeason, undefined)).mockResolvedValueOnce(refusal(deleteSeason, {}))
    vi.mocked(deleteTeam).mockResolvedValueOnce(answer(deleteTeam, undefined)).mockResolvedValueOnce(refusal(deleteTeam, {}))

    await expect(unfieldTeamFromSeason(1, "VAL", 20)).resolves.toEqual({ok: true})
    expect(unfieldTeam).toHaveBeenCalledWith({path: {seasonId: 20, teamId: 1}, query: {game: "VAL"}})
    await expect(unfieldTeamFromSeason(1, "VAL", 20)).resolves.toEqual({ok: false, reason: "The team could not be dropped from the season."})
    await expect(dropSeasonOrReason(20)).resolves.toEqual({ok: true})
    await expect(dropSeasonOrReason(20)).resolves.toEqual({ok: false, reason: "The season could not be removed."})
    await expect(dropTeam(1)).resolves.toEqual({ok: true})
    await expect(dropTeam(1)).resolves.toEqual({ok: false, reason: "The team could not be removed."})
  })
})
