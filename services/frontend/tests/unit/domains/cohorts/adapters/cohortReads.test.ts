import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  fetchCohortTargets,
  fetchCohort,
  fetchCohorts,
} from "@/domains/cohorts/adapters/cohorts"
import {findCohortById, findCohorts, listTargetOptions} from "@/services/api"
import {answer, emptyAnswer} from "../../../helpers/sdkAnswers"
import {TargetKind, CohortCategory, CohortType, TargetSystem} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCohortById: vi.fn(),
  findCohorts: vi.fn(),
  listTargetOptions: vi.fn(),
}))

/** A member as the api sends one, with only the fields it always sends. */
const rawMember = (over: Record<string, unknown> = {}) => ({
  targetMemberId: 1,
  isUserDeleted: false,
  joinedAt: "2026-01-05T10:00:00Z",
  ...over,
})

const rawCohort = (over: Record<string, unknown> = {}) => ({
  id: 7,
  label: "Newsletter",
  category: CohortCategory.MEMBERS,
  type: CohortType.NEWSLETTER_SUBSCRIBERS,
  orphaned: false,
  mappings: [],
  members: [],
  resolutions: [],
  ...over,
})

describe("a cohort cohort arrives with its absences already decided", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("a cohort the api would not give reads as nothing rather than as an empty cohort", async () => {
    vi.mocked(findCohortById).mockResolvedValue(emptyAnswer(findCohortById))

    await expect(fetchCohort(7)).resolves.toBeNull()
    expect(findCohortById).toHaveBeenCalledWith({path: {id: 7}})
  })

  it("every field the api may leave out comes back as nothing, not as undefined", async () => {
    vi.mocked(findCohortById).mockResolvedValue(answer(findCohortById, rawCohort({members: [rawMember({state: "SYNCED"})]})))

    const cohort = await fetchCohort(7)

    expect(cohort).toMatchObject({description: null, definitionKey: null})
    expect(cohort?.members[0]).toMatchObject({
      userId: null,
      userFullName: null,
      userEmail: null,
      externalLabel: null,
      externalUserId: null,
      system: null,
    })
  })

  it("a row the api does not vouch for reads as broken", async () => {
    const states = [undefined, "INVALID", "DESIRED", "STRANGER", "SYNCED", "VERIFIED"]
    vi.mocked(findCohortById).mockResolvedValue(answer(findCohortById, rawCohort({
        members: states.map((state, index) => rawMember({targetMemberId: index, state})),
      })))

    const cohort = await fetchCohort(7)

    expect(cohort?.members.map((member) => member.sync)).toEqual([
      "BROKEN",
      "BROKEN",
      "ONLY_HERE",
      "ONLY_EXTERNAL",
      "IN_SYNC",
      "IN_SYNC",
    ])
  })

  it("a target's runs keep their counts, and a run too old to say what started it names nothing", async () => {
    vi.mocked(findCohortById).mockResolvedValue(answer(findCohortById, rawCohort({
        mappings: [{targetId: 3, system: TargetSystem.BREVO, kind: TargetKind.LIST, label: "Newsletter", path: [], folderKnown: true, enforced: false,
          runs: [{startedAt: "2026-09-29T03:00:00Z", inSync: 40, oursOnly: 1, theirsOnly: 2}]}],
      })))

    const cohort = await fetchCohort(7)

    expect(cohort?.mappings[0]?.runs).toEqual([{startedAt: "2026-09-29T03:00:00Z", trigger: null, inStep: 40, missing: 1, extra: 2}])
  })

  it("a target that has never agreed, and one filed nowhere, both read as nothing", async () => {
    vi.mocked(findCohortById).mockResolvedValue(answer(findCohortById, rawCohort({
        mappings: [{targetId: 3, system: TargetSystem.BREVO, kind: TargetKind.LIST, label: "Newsletter", path: [], folderKnown: false, runs: [], enforced: false}],
      })))

    const cohort = await fetchCohort(7)

    expect(cohort?.mappings[0]).toEqual({
      targetId: 3,
      system: TargetSystem.BREVO,
      kind: TargetKind.LIST,
      label: "Newsletter",
      externalId: null,
      lastReconciledAt: null,
      path: [],
      folderKnown: false,
      runs: [],
      enforced: false,
    })
  })

  it("a resolution by the api itself names nobody as its maker", async () => {
    vi.mocked(findCohortById).mockResolvedValue(answer(findCohortById, rawCohort({
        resolutions: [{targetId: 3, system: TargetSystem.BREVO, action: "REMOVE", resolvedAt: "2026-09-29T20:00:00Z"}],
      })))

    const cohort = await fetchCohort(7)

    expect(cohort?.resolutions).toEqual([
      {system: TargetSystem.BREVO, action: "REMOVE", personName: null, resolvedByName: null, resolvedAt: "2026-09-29T20:00:00Z"},
    ])
  })

  it("a listing that came back with nothing reads as no cohorts", async () => {
    vi.mocked(findCohorts).mockResolvedValue(emptyAnswer(findCohorts))

    await expect(fetchCohorts()).resolves.toEqual([])
  })

  it("a listed cohort carries only what a row needs", async () => {
    vi.mocked(findCohorts).mockResolvedValue(answer(findCohorts, [{
        id: 7,
        label: "Newsletter",
        category: CohortCategory.MEMBERS,
        type: CohortType.NEWSLETTER_SUBSCRIBERS,
        memberCount: 12,
        mappingCount: 1,
        definitionKey: "NEWSLETTER_SUBSCRIBERS",
        targets: [{system: TargetSystem.BREVO, label: "Newsletter", made: true}],
      }]))

    await expect(fetchCohorts()).resolves.toEqual([{
      id: 7,
      label: "Newsletter",
      category: CohortCategory.MEMBERS,
      type: CohortType.NEWSLETTER_SUBSCRIBERS,
      memberCount: 12,
      mappingCount: 1,
      definitionKey: "NEWSLETTER_SUBSCRIBERS",
      targets: [{system: TargetSystem.BREVO, label: "Newsletter", made: true}],
    }])
  })
})

describe("the cohorts a picker offers", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("come by system and then by name, whatever order they were listed in", async () => {
    vi.mocked(listTargetOptions).mockResolvedValue(answer(listTargetOptions, [
        {id: 1, label: "Zebras", system: TargetSystem.BREVO, kind: TargetKind.LIST, memberCount: 2},
        {id: 2, label: "Alpacas", system: TargetSystem.GOOGLE_CALENDAR, kind: TargetKind.ROLE, memberCount: 3},
        {id: 3, label: "Antelopes", system: TargetSystem.BREVO, kind: TargetKind.LIST, memberCount: 4},
      ]))

    const options = await fetchCohortTargets()

    expect(options.map((option) => option.id)).toEqual([3, 1, 2])
  })

  it("read as none where the listing said nothing", async () => {
    vi.mocked(listTargetOptions).mockResolvedValue(emptyAnswer(listTargetOptions))

    await expect(fetchCohortTargets()).resolves.toEqual([])
  })
})
