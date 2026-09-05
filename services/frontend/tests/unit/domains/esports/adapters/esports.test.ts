import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  addGameOrReason,
  addToRoster,
  dropGameAccount,
  dropGameOrReason,
  enterGameInSeason,
  fieldTeamInSeason,
  leaveGameInSeason,
  linkRosterMember,
  loadEsportsPage,
  loadGameAccounts,
  loadGameContents,
  loadGames,
  loadSeasonContents,
  loadSeasonGames,
  loadTeams,
  saveGameAccount,
  saveGameOrReason,
  saveSeasonOrReason,
  storePicture,
} from "@/domains/esports/adapters/esports"
import {
  addRosterEntry,
  apiUrl,
  clearGameAccount,
  createGame,
  createSeason,
  deleteGame,
  enterGame,
  fieldTeam,
  findGame,
  findGameAccounts,
  findGameContents,
  findGames,
  findSeasonContents,
  findSeasonGames,
  findTeams,
  leaveGame,
  linkRosterEntry,
  setGameAccount,
  updateGame,
  uploadPublicImage,
} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  addRosterEntry: vi.fn(),
  clearGameAccount: vi.fn(),
  createGame: vi.fn(),
  createSeason: vi.fn(),
  deleteGame: vi.fn(),
  enterGame: vi.fn(),
  fieldTeam: vi.fn(),
  findGame: vi.fn(),
  findGameAccounts: vi.fn(),
  findGameContents: vi.fn(),
  findGames: vi.fn(),
  findSeasonContents: vi.fn(),
  findSeasonGames: vi.fn(),
  findTeams: vi.fn(),
  leaveGame: vi.fn(),
  linkRosterEntry: vi.fn(),
  setGameAccount: vi.fn(),
  updateGame: vi.fn(),
  uploadPublicImage: vi.fn(),
}))

/** An image as the api answers with one: paths of its own, which the adapter has to resolve. */
const picture = (path: string) => ({
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
    vi.mocked(findGames).mockResolvedValue({
      data: [{code: "VAL", name: "Valorant", banner: picture("/media/val.png"), icon: null}],
    } as never)

    const [game] = await loadGames()

    expect(game.banner?.url).toBe(apiUrl("/media/val.png"))
    expect(game.banner?.renditions[0]?.url).toBe(apiUrl("/media/val.png?w=600"))
  })

  // Every page asks for this, including ones served before the api is reachable, so a body that
  // is not the list it was promised reads as no games rather than taking the navigation down.
  it("answers with no games at all where the body was not a list", async () => {
    vi.mocked(findGames).mockResolvedValue({data: {message: "no"}} as never)

    await expect(loadGames()).resolves.toEqual([])
  })
})

describe("addGameOrReason", () => {
  it("answers with the refusal in the api's own words", async () => {
    vi.mocked(createGame).mockResolvedValue({
      error: {code: "GameAlreadyExists", gameName: "Valorant"},
    } as never)

    await expect(addGameOrReason({name: "Valorant", slug: "valorant"}))
      .resolves.toEqual({ok: false, reason: "Valorant is already a game."})
  })

  // The success guard reads `!res.data` as well as the error: a game answered for with nothing
  // has no code, and the caller goes on to write teams and rosters against one.
  it("refuses a write the api answered with nothing at all", async () => {
    vi.mocked(createGame).mockResolvedValue({data: undefined} as never)

    await expect(addGameOrReason({name: "Valorant", slug: "valorant"}))
      .resolves.toEqual({ok: false, reason: "The game could not be added."})
  })
})

describe("saveGameOrReason", () => {
  it("names the game by its code in the path and never in the body, the code being what everything points at", async () => {
    vi.mocked(updateGame).mockResolvedValue({data: {code: "VAL", name: "Valorant"}} as never)

    await saveGameOrReason("VAL", {
      name: "Valorant", slug: "valorant", intro: null, accent: null, banner: null, icon: null, sortIndex: 2,
    })

    const sent = optionsOf(vi.mocked(updateGame).mock.calls[0]?.[0])
    expect(sent.path).toEqual({game: "VAL"})
    expect(sent.body).not.toHaveProperty("code")
  })
})

describe("loadGameContents", () => {
  // A failed read is not an empty game: reported as one, the board is offered a removal while
  // being told the game holds nothing.
  it("answers with nothing at all where the read failed, rather than with an empty game", async () => {
    vi.mocked(findGameContents).mockResolvedValue({error: {status: 500}, data: undefined} as never)

    await expect(loadGameContents("VAL")).resolves.toBeNull()
  })

  it("answers with what the game holds", async () => {
    vi.mocked(findGameContents).mockResolvedValue({data: {teams: 3, players: 14}} as never)

    await expect(loadGameContents("VAL")).resolves.toEqual({teams: 3, players: 14})
  })
})

describe("loadSeasonContents", () => {
  // The opposite reading to `loadGameContents`, and deliberate rather than an oversight: this
  // one answers zero where it could not read, so the offer to remove says the season is empty.
  it("answers that the season holds nothing where the read failed", async () => {
    vi.mocked(findSeasonContents).mockResolvedValue({error: {status: 500}, data: undefined} as never)

    await expect(loadSeasonContents(19)).resolves.toEqual({teams: 0, players: 0})
  })
})

describe("dropGameOrReason", () => {
  it("answers with the api's account of what the game holds", async () => {
    vi.mocked(deleteGame).mockResolvedValue({
      error: {code: "GameHoldsHistory", gameName: "Valorant", teams: 3, players: 14},
    } as never)

    const answer = await dropGameOrReason("VAL")

    expect(answer).toMatchObject({ok: false})
    expect((answer as {reason: string}).reason).toContain("3 teams and 14 people")
  })

  // The guard is on the error alone, a removal having no body to answer with.
  it("counts an answer carrying nothing as a removal", async () => {
    vi.mocked(deleteGame).mockResolvedValue({data: undefined} as never)

    await expect(dropGameOrReason("VAL")).resolves.toEqual({ok: true})
  })
})

describe("storePicture", () => {
  it("answers with the whole image, every width of it drawn against the api", async () => {
    vi.mocked(uploadPublicImage).mockResolvedValue({data: picture("/media/banner.png")} as never)

    const stored = await storePicture(new File([], "banner.png"), "GAME_BANNER" as never)

    expect(stored).toMatchObject({ok: true})
    expect((stored as {picture: {url: string}}).picture.url).toBe(apiUrl("/media/banner.png"))
  })

  // "Something went wrong" does not tell somebody to pick another file.
  it("answers a refused upload in the api's own words", async () => {
    vi.mocked(uploadPublicImage).mockResolvedValue({error: {detail: "That file is not an image."}} as never)

    await expect(storePicture(new File([], "notes.txt"), "GAME_BANNER" as never))
      .resolves.toEqual({ok: false, reason: "That file is not an image."})
  })
})

describe("loadEsportsPage", () => {
  it("draws every team's art, and every person's, against the api", async () => {
    vi.mocked(findGame).mockResolvedValue({
      data: {
        game: "VAL",
        season: {id: 20},
        seasons: [],
        teams: [{
          id: 1, name: "BS Waterboarders", banner: picture("/media/team.png"), icon: null,
          members: [{id: 5, handle: "nova", icon: picture("/media/nova.png")}],
        }],
      },
    } as never)

    const page = await loadEsportsPage("VAL", 20)

    expect(page?.teams[0]?.banner?.url).toBe(apiUrl("/media/team.png"))
    expect(page?.teams[0]?.members[0]?.icon?.url).toBe(apiUrl("/media/nova.png"))
  })

  it("asks about no season in particular where none was named, the api choosing one", async () => {
    vi.mocked(findGame).mockResolvedValue({data: {game: "VAL", season: {id: 20}, seasons: [], teams: []}} as never)

    await loadEsportsPage("VAL")

    expect(optionsOf(vi.mocked(findGame).mock.calls[0]?.[0]).query).toEqual({})
  })

  it("answers with nothing at all where there was no page", async () => {
    vi.mocked(findGame).mockResolvedValue({error: {status: 404}, data: undefined} as never)

    await expect(loadEsportsPage("VAL", 20)).resolves.toBeNull()
  })
})

describe("saveSeasonOrReason", () => {
  it("writes a season that has no id yet, and corrects one that has", async () => {
    vi.mocked(createSeason).mockResolvedValue({data: {id: 21}} as never)

    await saveSeasonOrReason({name: "Autumn 2025", startDate: "2025-09-01", endDate: "2026-01-31"})

    expect(createSeason).toHaveBeenCalled()
  })

  it("answers with the api's own account of dates that overlap another season", async () => {
    vi.mocked(createSeason).mockResolvedValue({
      error: {code: "SeasonDatesOverlap", seasonName: "Spring 2025"},
    } as never)

    await expect(saveSeasonOrReason({name: "Autumn 2025", startDate: "2025-01-01", endDate: "2026-01-31"}))
      .resolves.toEqual({ok: false, reason: "Those dates overlap Spring 2025."})
  })

  // This one guards on the error alone, so a success may carry no season. `ok` therefore does
  // not promise one, and a caller reading `season` has to allow for its absence.
  it("counts an answer carrying no season as a write that landed, carrying no season", async () => {
    vi.mocked(createSeason).mockResolvedValue({data: undefined} as never)

    await expect(saveSeasonOrReason({name: "Autumn 2025", startDate: "2025-09-01", endDate: "2026-01-31"}))
      .resolves.toEqual({ok: true, season: null})
  })
})

describe("loadSeasonGames", () => {
  it("answers with each game the season ran, and whether a visitor sees it", async () => {
    vi.mocked(findSeasonGames).mockResolvedValue({
      data: [{game: "VAL", public: false, teams: [{id: 1, name: "BS Waterboarders", members: []}]}],
    } as never)

    await expect(loadSeasonGames(20)).resolves.toMatchObject([{game: "VAL", public: false}])
  })

  it("answers with no games where the read failed", async () => {
    vi.mocked(findSeasonGames).mockResolvedValue({error: {status: 500}, data: undefined} as never)

    await expect(loadSeasonGames(20)).resolves.toEqual([])
  })
})

describe("enterGameInSeason", () => {
  it("answers with the game entered, holding nobody until a team is fielded in it", async () => {
    vi.mocked(enterGame).mockResolvedValue({data: {game: "VAL", public: false}} as never)

    await expect(enterGameInSeason(20, "VAL")).resolves.toEqual({game: "VAL", teams: [], public: false})
  })

  // The odd one out: every other write in this adapter answers a refusal with the api's words,
  // and this one throws them away, so a caller can say only that nothing happened.
  it("answers with nothing at all where the entry was refused, keeping none of the reason", async () => {
    vi.mocked(enterGame).mockResolvedValue({
      error: {code: "GameFieldedInSeason", gameName: "Valorant", teams: 2},
    } as never)

    await expect(enterGameInSeason(20, "VAL")).resolves.toBeNull()
  })
})

describe("leaveGameInSeason", () => {
  it("answers with the api's account of the teams still in the season", async () => {
    vi.mocked(leaveGame).mockResolvedValue({
      error: {code: "GameFieldedInSeason", gameName: "Valorant", teams: 2},
    } as never)

    const answer = await leaveGameInSeason(20, "VAL")

    expect((answer as {reason: string}).reason).toContain("Valorant still has 2 teams")
  })
})

describe("fieldTeamInSeason", () => {
  it("brings the line-up across from the fielding that was chosen", async () => {
    vi.mocked(fieldTeam).mockResolvedValue({data: {team: {id: 7}}} as never)

    await fieldTeamInSeason(7, "VAL", 20, true, null, {game: "CS2", seasonId: 19})

    expect(optionsOf(vi.mocked(fieldTeam).mock.calls[0]?.[0]).body)
      .toMatchObject({game: "VAL", carryLineup: true, carryFrom: {game: "CS2", seasonId: 19}})
  })

  // Naming no banner leaves the art alone: a team is re-fielded to say it plays this season as
  // often as to change its picture.
  it("says nothing about the banner where none was named, rather than taking it away", async () => {
    vi.mocked(fieldTeam).mockResolvedValue({data: {team: {id: 7}}} as never)

    await fieldTeamInSeason(7, "VAL", 20, false)

    expect(optionsOf(vi.mocked(fieldTeam).mock.calls[0]?.[0]).body.banner).toBeUndefined()
  })

  // The body is what says the fielding happened; the roster writes that follow would otherwise
  // land on a fielding nobody confirmed.
  it("refuses a fielding the api answered with nothing at all", async () => {
    vi.mocked(fieldTeam).mockResolvedValue({data: undefined} as never)

    await expect(fieldTeamInSeason(7, "VAL", 20, false))
      .resolves.toEqual({ok: false, reason: "That team could not be fielded this season."})
  })
})

describe("addToRoster", () => {
  it("refuses an entry the api answered with nothing, so nobody is reported as put on", async () => {
    vi.mocked(addRosterEntry).mockResolvedValue({data: undefined} as never)

    await expect(addToRoster(7, {game: "VAL", seasonId: 20, handle: "nova", role: "PLAYER"}))
      .resolves.toEqual({ok: false, reason: "That person could not be put on the roster."})
  })
})

describe("linkRosterMember", () => {
  // Detaching, which is how a roster entry is kept for somebody the association has no member for.
  it("names no member at all where the entry is being detached", async () => {
    vi.mocked(linkRosterEntry).mockResolvedValue({data: {id: 21}} as never)

    await linkRosterMember(21, null)

    expect(optionsOf(vi.mocked(linkRosterEntry).mock.calls[0]?.[0]).body).toEqual({userId: undefined})
  })
})

describe("loadTeams", () => {
  it("answers with no teams where the read failed, the pool being shared and read on every page", async () => {
    vi.mocked(findTeams).mockResolvedValue({error: {status: 500}, data: undefined} as never)

    await expect(loadTeams()).resolves.toEqual([])
  })
})

describe("loadGameAccounts", () => {
  it("answers with no handles where the read failed", async () => {
    vi.mocked(findGameAccounts).mockResolvedValue({error: {status: 500}, data: undefined} as never)

    await expect(loadGameAccounts(5)).resolves.toEqual([])
  })
})

describe("a member's game handle", () => {
  // These two throw rather than answering a shrug: a discarded refusal left the handle looking
  // saved, with only the still-enabled button to hint otherwise.
  it("asks the sdk to throw, so the editor's own catch is the thing that reports a refusal", async () => {
    vi.mocked(setGameAccount).mockResolvedValue({data: {game: "VAL", handle: "nova"}} as never)
    vi.mocked(clearGameAccount).mockResolvedValue({data: undefined} as never)

    await saveGameAccount(5, "VAL", "nova")
    await dropGameAccount(5, "VAL")

    expect(optionsOf(vi.mocked(setGameAccount).mock.calls[0]?.[0]).throwOnError).toBe(true)
    expect(optionsOf(vi.mocked(clearGameAccount).mock.calls[0]?.[0]).throwOnError).toBe(true)
  })
})
