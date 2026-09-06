import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  fetchCohortOptions,
  fetchCohortSubject,
  fetchCohortSubjects,
  queueCohortJob,
} from "@/domains/cohorts/adapters/cohorts"
import {enqueue, findCohortSubjectById, findCohortSubjects, findCohorts} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  enqueue: vi.fn(),
  findCohortSubjectById: vi.fn(),
  findCohortSubjects: vi.fn(),
  findCohorts: vi.fn(),
}))

/** A member as the api sends one, with only the fields it always sends. */
const rawMember = (over: Record<string, unknown> = {}) => ({
  cohortMemberId: 1,
  isUserDeleted: false,
  joinedAt: "2026-01-05T10:00:00Z",
  ...over,
})

const rawSubject = (over: Record<string, unknown> = {}) => ({
  id: 7,
  label: "Newsletter",
  category: "MEMBERS",
  type: "NEWSLETTER_SUBSCRIBERS",
  orphaned: false,
  mappings: [],
  members: [],
  ...over,
})

describe("a cohort subject arrives with its absences already decided", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("a subject the api would not give reads as nothing rather than as an empty cohort", async () => {
    vi.mocked(findCohortSubjectById).mockResolvedValue({data: undefined} as never)

    await expect(fetchCohortSubject(7)).resolves.toBeNull()
    expect(findCohortSubjectById).toHaveBeenCalledWith({path: {id: 7}})
  })

  it("every field the api may leave out comes back as nothing, not as undefined", async () => {
    vi.mocked(findCohortSubjectById).mockResolvedValue({
      data: rawSubject({members: [rawMember({state: "SYNCED"})]}),
    } as never)

    const subject = await fetchCohortSubject(7)

    expect(subject).toMatchObject({description: null, definitionKey: null})
    expect(subject?.members[0]).toMatchObject({
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
    vi.mocked(findCohortSubjectById).mockResolvedValue({
      data: rawSubject({
        members: states.map((state, index) => rawMember({cohortMemberId: index, state})),
      }),
    } as never)

    const subject = await fetchCohortSubject(7)

    expect(subject?.members.map((member) => member.sync)).toEqual([
      "BROKEN",
      "BROKEN",
      "ONLY_HERE",
      "ONLY_EXTERNAL",
      "IN_SYNC",
      "IN_SYNC",
    ])
  })

  it("a target that has never agreed, and one filed nowhere, both read as nothing", async () => {
    vi.mocked(findCohortSubjectById).mockResolvedValue({
      data: rawSubject({
        mappings: [{cohortId: 3, system: "BREVO", kind: "LIST", label: "Newsletter", path: []}],
      }),
    } as never)

    const subject = await fetchCohortSubject(7)

    expect(subject?.mappings[0]).toEqual({
      cohortId: 3,
      system: "BREVO",
      kind: "LIST",
      label: "Newsletter",
      externalId: null,
      lastReconciledAt: null,
      path: [],
    })
  })

  it("a listing that came back with nothing reads as no cohorts", async () => {
    vi.mocked(findCohortSubjects).mockResolvedValue({data: undefined} as never)

    await expect(fetchCohortSubjects()).resolves.toEqual([])
  })

  it("a listed cohort carries only what a row needs", async () => {
    vi.mocked(findCohortSubjects).mockResolvedValue({
      data: [{
        id: 7,
        label: "Newsletter",
        category: "MEMBERS",
        type: "NEWSLETTER_SUBSCRIBERS",
        memberCount: 12,
        mappingCount: 1,
      }],
    } as never)

    await expect(fetchCohortSubjects()).resolves.toEqual([{
      id: 7,
      label: "Newsletter",
      category: "MEMBERS",
      type: "NEWSLETTER_SUBSCRIBERS",
      memberCount: 12,
      mappingCount: 1,
    }])
  })
})

describe("the cohorts a picker offers", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("come by system and then by name, whatever order they were listed in", async () => {
    vi.mocked(findCohorts).mockResolvedValue({
      data: [
        {id: 1, label: "Zebras", system: "BREVO", kind: "LIST", memberCount: 2},
        {id: 2, label: "Alpacas", system: "DISCORD", kind: "ROLE", memberCount: 3},
        {id: 3, label: "Antelopes", system: "BREVO", kind: "LIST", memberCount: 4},
      ],
    } as never)

    const options = await fetchCohortOptions()

    expect(options.map((option) => option.id)).toEqual([3, 1, 2])
  })

  it("read as none where the listing said nothing", async () => {
    vi.mocked(findCohorts).mockResolvedValue({data: undefined} as never)

    await expect(fetchCohortOptions()).resolves.toEqual([])
  })
})

describe("a cohort job answers rather than throwing", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("carries the id it was queued under", async () => {
    vi.mocked(enqueue).mockResolvedValue({status: 200, data: {id: 42}} as never)

    await expect(queueCohortJob("cohort.evaluate-user", {userId: 3})).resolves.toEqual({
      ok: true,
      jobId: 42,
    })
    expect(enqueue).toHaveBeenCalledWith({
      body: {jobType: "cohort.evaluate-user", payload: {userId: 3}},
    })
  })

  it("queues with nothing where the job takes no payload", async () => {
    vi.mocked(enqueue).mockResolvedValue({status: 200, data: {}} as never)

    await expect(queueCohortJob("cohort.reconcile-all-users")).resolves.toEqual({
      ok: true,
      jobId: null,
    })
    expect(enqueue).toHaveBeenCalledWith({
      body: {jobType: "cohort.reconcile-all-users", payload: {}},
    })
  })

  it("a refusal is an answer, not a thrown error", async () => {
    vi.mocked(enqueue).mockResolvedValue({status: 403, data: undefined} as never)

    await expect(queueCohortJob("cohort.evaluate-user", {userId: 3})).resolves.toEqual({ok: false})
  })
})
