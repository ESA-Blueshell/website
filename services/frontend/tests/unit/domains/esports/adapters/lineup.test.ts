import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  fieldExistingTeam,
  isBlank,
  publishLineup,
  type DraftEntry,
  type LineupDraft,
} from "@/domains/esports/adapters/lineup"
import {addRosterEntry, fieldTeam, publishLineup as sendLineup} from "@/services/api"
import {TeamRole} from "@/services/api"
import {aRosterEntry, aSeason, aTeam} from "../../../helpers/apiFixtures"
import {answer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  addRosterEntry: vi.fn(),
  fieldTeam: vi.fn(),
  publishLineup: vi.fn(),
}))

const entry = (over: Partial<DraftEntry> = {}): DraftEntry => ({
  id: null, handle: "nova", role: TeamRole.PLAYER, roleTitle: "", description: "", userId: null,
  displayName: "", icon: null, ...over,
})

const blank = (): DraftEntry => entry({handle: ""})

const draft = (over: Partial<LineupDraft> = {}): LineupDraft => ({
  teamId: 7, name: "Blueshell", game: "VAL", seasonId: 3, banner: null, icon: null,
  removed: [], entries: [], ...over,
})

/** Every write answers yes, so a test says which of them it is about by overriding one. */
const everythingLands = () => {
  vi.mocked(sendLineup).mockResolvedValue(answer(sendLineup, {team: aTeam({id: 7}), roster: []}))
  vi.mocked(fieldTeam).mockResolvedValue(answer(fieldTeam, {team: aTeam({id: 7}), game: "VAL", season: aSeason({id: 3}), carried: []}))
  vi.mocked(addRosterEntry).mockResolvedValue(answer(addRosterEntry, aRosterEntry({id: 30})))
}

const bodyOf = (call: unknown) => (call as {body: Record<string, unknown>}).body

beforeEach(() => {
  vi.clearAllMocks()
  everythingLands()
})

describe("publishLineup", () => {
  const sent = () => vi.mocked(sendLineup).mock.calls[0]?.[0] as {path: {seasonId: number}; body: Record<string, unknown>}

  it("sends the whole draft in one request, for the season it is in", async () => {
    await publishLineup(draft({removed: [21], banner: "banners/b.webp", entries: [entry({id: 22, userId: 4})]}))

    expect(sendLineup).toHaveBeenCalledTimes(1)
    expect(sent().path).toEqual({seasonId: 3})
    expect(sent().body).toMatchObject({
      teamId: 7, name: "Blueshell", game: "VAL", banner: "banners/b.webp", removed: [21],
      entries: [{id: 22, handle: "nova", role: "PLAYER", userId: 4, roleTitle: null, description: null}],
    })
  })

  it("names no team where it does not exist yet, so the api makes it", async () => {
    await publishLineup(draft({teamId: null}))

    expect(sent().body.teamId).toBeNull()
  })

  // A blank row is not a place somebody stood, so it is not sent, and the api numbers what is.
  it("leaves out the rows nobody typed into", async () => {
    await publishLineup(draft({entries: [entry(), blank(), entry({handle: "kite"})]}))

    expect((sent().body.entries as Array<{handle: string}>).map(one => one.handle)).toEqual(["nova", "kite"])
  })

  // The api applies the draft whole or not at all, so a refusal is only a reason.
  it("answers a refusal with the api's reason and nothing about what landed", async () => {
    vi.mocked(sendLineup).mockResolvedValue(refusal(sendLineup, {detail: "Not this season."}))

    expect(await publishLineup(draft())).toEqual({ok: false, reason: "Not this season."})
  })

  it("answers a throw with the refusal a refused save would have answered with", async () => {
    vi.mocked(sendLineup).mockRejectedValue(new Error("offline"))

    expect(await publishLineup(draft())).toEqual({ok: false, reason: "The line-up could not be saved."})
  })

  it("answers a save the api took", async () => {
    expect(await publishLineup(draft())).toEqual({ok: true, teamId: 7})
  })
})

describe("fieldExistingTeam", () => {
  const from = {game: "VAL", seasonId: 2}
  const fielding = (over: Partial<Parameters<typeof fieldExistingTeam>[0]> = {}) => ({
    teamId: 9, game: "VAL", seasonId: 3, from, entries: [entry(), entry({handle: "kite"})],
    sourceSize: 2, unread: false, ...over,
  })

  // `carryFrom` has the api copy the whole line-up, and an unread source carries nobody, so
  // neither half of this may run on one. The stage is its own, so the component can tell this
  // refusal from a fielding the api argued with and write the sentence for it.
  it("writes nothing at all where the line-up being carried could not be read", async () => {
    const done = await fieldExistingTeam(fielding({unread: true}))

    expect(done).toMatchObject({ok: false, written: 0, stage: "source"})
    expect(fieldTeam).not.toHaveBeenCalled()
    expect(addRosterEntry).not.toHaveBeenCalled()
  })

  it("has the api carry a line-up nobody was dropped from", async () => {
    await fieldExistingTeam(fielding())

    expect(bodyOf(vi.mocked(fieldTeam).mock.calls[0]?.[0])).toMatchObject({carryFrom: from})
    expect(addRosterEntry).not.toHaveBeenCalled()
  })

  // Carried by hand, so nobody who was dropped is written down and then deleted.
  it("carries the people kept by hand where any of them were dropped", async () => {
    await fieldExistingTeam(fielding({entries: [entry()], sourceSize: 2}))

    expect(bodyOf(vi.mocked(fieldTeam).mock.calls[0]?.[0]).carryFrom).toBeUndefined()
    expect(addRosterEntry).toHaveBeenCalledTimes(1)
  })

  it("says how many were carried across before the one that was refused", async () => {
    vi.mocked(addRosterEntry)
      .mockResolvedValueOnce(answer(addRosterEntry, aRosterEntry({id: 30})))
      .mockResolvedValueOnce(refusal(addRosterEntry, {detail: "Nope."}))

    const done = await fieldExistingTeam(fielding({sourceSize: 3}))

    expect(done).toEqual({ok: false, reason: "Nope.", written: 1, stage: "carry"})
  })

  it("carries nobody where the fielding itself was refused", async () => {
    vi.mocked(fieldTeam).mockResolvedValue(refusal(fieldTeam, {detail: "Not this season."}))

    const done = await fieldExistingTeam(fielding({sourceSize: 3}))

    expect(done).toMatchObject({ok: false, written: 0, stage: "fielding"})
    expect(addRosterEntry).not.toHaveBeenCalled()
  })
})

describe("isBlank", () => {
  it("holds for a row nobody typed into", () => {
    expect(isBlank(blank())).toBe(true)
  })

  it("does not hold for a row carrying anything at all", () => {
    expect(isBlank(entry({handle: "", description: "A word about them"}))).toBe(false)
    expect(isBlank(entry({handle: "", userId: 4}))).toBe(false)
    expect(isBlank(entry({handle: "", icon: "roster-icons/one.webp"}))).toBe(false)
  })
})
