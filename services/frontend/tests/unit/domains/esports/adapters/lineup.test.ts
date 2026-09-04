import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  fieldExistingTeam,
  isBlank,
  publishLineup,
  type DraftEntry,
  type LineupDraft,
} from "@/domains/esports/adapters/lineup"
import {
  addRosterEntry,
  createTeam,
  fieldTeam,
  linkRosterEntry,
  removeRosterEntry,
  updateRosterEntry,
  updateTeam,
} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  addRosterEntry: vi.fn(),
  createTeam: vi.fn(),
  fieldTeam: vi.fn(),
  linkRosterEntry: vi.fn(),
  removeRosterEntry: vi.fn(),
  updateRosterEntry: vi.fn(),
  updateTeam: vi.fn(),
}))

const entry = (over: Partial<DraftEntry> = {}): DraftEntry => ({
  id: null, handle: "nova", role: "PLAYER", roleTitle: "", description: "", userId: null,
  displayName: "", icon: null, ...over,
})

const blank = (): DraftEntry => entry({handle: ""})

const draft = (over: Partial<LineupDraft> = {}): LineupDraft => ({
  teamId: 7, name: "Blueshell", game: "VAL", seasonId: 3, banner: null, icon: null,
  removed: [], entries: [], ...over,
})

/** Every write answers yes, so a test says which of them it is about by overriding one. */
const everythingLands = () => {
  vi.mocked(createTeam).mockResolvedValue({data: {id: 9, name: "Blueshell"}} as never)
  vi.mocked(updateTeam).mockResolvedValue({data: {id: 7, name: "Blueshell"}} as never)
  vi.mocked(fieldTeam).mockResolvedValue({data: {team: {id: 7}}} as never)
  vi.mocked(removeRosterEntry).mockResolvedValue({data: undefined} as never)
  vi.mocked(addRosterEntry).mockResolvedValue({data: {id: 30}} as never)
  vi.mocked(updateRosterEntry).mockResolvedValue({data: {id: 21}} as never)
  vi.mocked(linkRosterEntry).mockResolvedValue({data: {id: 21}} as never)
}

const bodyOf = (call: unknown) => (call as {body: Record<string, unknown>}).body

beforeEach(() => {
  vi.clearAllMocks()
  everythingLands()
})

describe("publishLineup", () => {
  it("writes the team, then the fielding, then the removals, then the roster", async () => {
    await publishLineup(draft({removed: [21], entries: [entry()]}))

    const order = [updateTeam, fieldTeam, removeRosterEntry, addRosterEntry]
      .map(one => vi.mocked(one).mock.invocationCallOrder[0])
    expect(order).toEqual([...order].sort((a, b) => Number(a) - Number(b)))
  })

  it("makes the team where there is none to save, and writes the line-up against the new one", async () => {
    await publishLineup(draft({teamId: null, entries: [entry()]}))

    expect(updateTeam).not.toHaveBeenCalled()
    expect(vi.mocked(addRosterEntry).mock.calls[0]?.[0]).toMatchObject({path: {teamId: 9}})
  })

  // Nothing after the refusal may run: a fielding written against a team that was meant to be
  // renamed is the half-written state the ordering exists to avoid.
  it("stops at the first refusal, and says which stage stopped it", async () => {
    vi.mocked(fieldTeam).mockResolvedValue({error: {detail: "Not this season."}} as never)

    const done = await publishLineup(draft({removed: [21], entries: [entry()]}))

    expect(done).toEqual({ok: false, reason: "Not this season.", written: 0, stage: "fielding"})
    expect(removeRosterEntry).not.toHaveBeenCalled()
    expect(addRosterEntry).not.toHaveBeenCalled()
  })

  it("counts the entries that landed before the one that was refused", async () => {
    vi.mocked(addRosterEntry)
      .mockResolvedValueOnce({data: {id: 30}} as never)
      .mockResolvedValueOnce({error: {detail: "Nope."}} as never)

    const done = await publishLineup(draft({entries: [entry(), entry({handle: "kite"}), entry()]}))

    expect(done).toEqual({ok: false, reason: "Nope.", written: 1, stage: "roster"})
    expect(addRosterEntry).toHaveBeenCalledTimes(2)
  })

  // A count of roster entries rather than of requests: a refusal before the roster wrote none
  // of it, whatever else it wrote.
  it("counts no entries for a refusal before the roster", async () => {
    vi.mocked(removeRosterEntry).mockResolvedValue({error: {detail: "Nope."}} as never)

    const done = await publishLineup(draft({removed: [21], entries: [entry()]}))

    expect(done).toMatchObject({written: 0, stage: "removals"})
  })

  it("leaves out the rows nobody typed into", async () => {
    await publishLineup(draft({entries: [entry(), blank(), entry({handle: "kite"})]}))

    expect(addRosterEntry).toHaveBeenCalledTimes(2)
    const written = vi.mocked(addRosterEntry).mock.calls.map(call => bodyOf(call[0]).handle)
    expect(written).toEqual(["nova", "kite"])
  })

  // A blank row is not a place somebody stood, so it does not take a position with it: the
  // positions are handed out after the blanks are gone, and run without a gap.
  it("numbers the entries that remain without a gap", async () => {
    await publishLineup(draft({
      entries: [entry({id: 21}), blank(), entry({id: 22, handle: "kite"})],
    }))

    const written = vi.mocked(updateRosterEntry).mock.calls.map(call => bodyOf(call[0]).sortIndex)
    expect(written).toEqual([0, 1])
  })

  it("corrects the entries that exist and attaches who they belong to", async () => {
    await publishLineup(draft({entries: [entry({id: 21, userId: 4})]}))

    expect(addRosterEntry).not.toHaveBeenCalled()
    expect(updateRosterEntry).toHaveBeenCalled()
    expect(linkRosterEntry).toHaveBeenCalledWith({path: {id: 21}, body: {userId: 4}})
  })

  // The sdk answers a refusal with a body, but a request that never reached it throws. Both
  // come back as the same refusal, so a caller has one failure to read rather than two.
  it("answers a throw with the refusal a refused write would have answered with", async () => {
    vi.mocked(fieldTeam).mockRejectedValue(new Error("offline"))

    const done = await publishLineup(draft({entries: [entry()]}))

    expect(done).toMatchObject({ok: false, stage: "fielding", written: 0})
    expect((done as {reason: string}).reason).toBe("The line-up could not be saved.")
  })

  // `ok` promising a team that is not there is how a line-up gets written against nothing.
  it("refuses a team saved with no answer, rather than writing a line-up against nothing", async () => {
    vi.mocked(createTeam).mockResolvedValue({data: undefined} as never)

    const done = await publishLineup(draft({teamId: null, entries: [entry()]}))

    expect(done).toMatchObject({ok: false, stage: "team"})
    expect(fieldTeam).not.toHaveBeenCalled()
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
      .mockResolvedValueOnce({data: {id: 30}} as never)
      .mockResolvedValueOnce({error: {detail: "Nope."}} as never)

    const done = await fieldExistingTeam(fielding({sourceSize: 3}))

    expect(done).toEqual({ok: false, reason: "Nope.", written: 1, stage: "carry"})
  })

  it("carries nobody where the fielding itself was refused", async () => {
    vi.mocked(fieldTeam).mockResolvedValue({error: {detail: "Not this season."}} as never)

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
