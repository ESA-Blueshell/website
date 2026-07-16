import {beforeEach, describe, expect, it, vi} from "vitest"
import {ref} from "vue"
import {usePaidToggle} from "@/composables/usePaidToggle"

const {mockCreateContribution, mockDeleteContribution, mockFindContributionsByPeriodId} = vi.hoisted(() => ({
  mockCreateContribution: vi.fn(),
  mockDeleteContribution: vi.fn(),
  mockFindContributionsByPeriodId: vi.fn(),
}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {
    ...actual,
    createContribution: mockCreateContribution,
    deleteContribution: mockDeleteContribution,
    findContributionsByPeriodId: mockFindContributionsByPeriodId,
  }
})

describe("usePaidToggle", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCreateContribution.mockResolvedValue({data: {userId: 1, contributionPeriodId: 5, version: 1, createdAt: "", updatedAt: ""}})
    mockDeleteContribution.mockResolvedValue({})
    mockFindContributionsByPeriodId.mockResolvedValue({data: []})
  })

  it("isDisabled is true when no period selected (selectedPeriodId=0)", () => {
    const paidUserIds = ref(new Set<number>())
    const {isDisabled} = usePaidToggle(paidUserIds)
    expect(isDisabled.value).toBe(true)
  })

  it("isDisabled is false after contributionPeriodChanged with a period", async () => {
    const paidUserIds = ref(new Set<number>())
    const {isDisabled, contributionPeriodChanged} = usePaidToggle(paidUserIds)

    await contributionPeriodChanged({id: 5, startDate: "2025-01-01", endDate: "2025-12-31"})
    expect(isDisabled.value).toBe(false)
  })

  it("contributionPeriodChanged populates paidUserIds from contributions", async () => {
    const paidUserIds = ref(new Set<number>())
    mockFindContributionsByPeriodId.mockResolvedValue({
      data: [{userId: 1, contributionPeriodId: 5, version: 1, createdAt: "", updatedAt: ""}],
    })
    const {contributionPeriodChanged} = usePaidToggle(paidUserIds)

    await contributionPeriodChanged({id: 5, startDate: "2025-01-01", endDate: "2025-12-31"})
    expect(paidUserIds.value.has(1)).toBe(true)
    expect(mockFindContributionsByPeriodId).toHaveBeenCalledWith({path: {periodId: 5}})
  })

  it("contributionPeriodChanged with undefined clears paidUserIds and sets selectedPeriodId to 0", async () => {
    const paidUserIds = ref(new Set<number>([1, 2]))
    const {contributionPeriodChanged, selectedPeriodId} = usePaidToggle(paidUserIds)

    await contributionPeriodChanged(undefined)
    expect(paidUserIds.value.size).toBe(0)
    expect(selectedPeriodId.value).toBe(0)
  })

  it("togglePaid does nothing when isDisabled (no period selected)", async () => {
    const paidUserIds = ref(new Set<number>())
    const {togglePaid} = usePaidToggle(paidUserIds)

    await togglePaid(1)
    expect(mockCreateContribution).not.toHaveBeenCalled()
    expect(mockDeleteContribution).not.toHaveBeenCalled()
  })

  it("togglePaid calls createContribution when user is unpaid", async () => {
    const paidUserIds = ref(new Set<number>())
    const {togglePaid, contributionPeriodChanged} = usePaidToggle(paidUserIds)

    await contributionPeriodChanged({id: 5, startDate: "2025-01-01", endDate: "2025-12-31"})
    await togglePaid(1)

    expect(mockCreateContribution).toHaveBeenCalledWith({body: {userId: 1, contributionPeriodId: 5}})
    expect(paidUserIds.value.has(1)).toBe(true)
  })

  it("togglePaid calls deleteContribution when user is already paid", async () => {
    const paidUserIds = ref(new Set<number>([1]))
    const {togglePaid, contributionPeriodChanged} = usePaidToggle(paidUserIds)

    await contributionPeriodChanged({id: 5, startDate: "2025-01-01", endDate: "2025-12-31"})
    // re-seed since contributionPeriodChanged resets paidUserIds from API (which returns empty)
    paidUserIds.value = new Set([1])

    await togglePaid(1)

    expect(mockDeleteContribution).toHaveBeenCalledWith({path: {contributionPeriodId: 5, userId: 1}})
    expect(paidUserIds.value.has(1)).toBe(false)
  })

  it("optimistic update adds user to paidUserIds before API resolves", async () => {
    let resolveFn!: () => void
    mockCreateContribution.mockReturnValue(new Promise<{data: object}>((resolve) => {
      resolveFn = () => resolve({data: {}})
    }))

    const paidUserIds = ref(new Set<number>())
    const {togglePaid, contributionPeriodChanged} = usePaidToggle(paidUserIds)

    await contributionPeriodChanged({id: 5, startDate: "2025-01-01", endDate: "2025-12-31"})

    const togglePromise = togglePaid(1)
    // Before API resolves, optimistic update should have applied
    expect(paidUserIds.value.has(1)).toBe(true)

    resolveFn()
    await togglePromise
    expect(paidUserIds.value.has(1)).toBe(true)
  })

  it("rollback on createContribution error removes user from paidUserIds", async () => {
    mockCreateContribution.mockRejectedValue(new Error("Network error"))

    const paidUserIds = ref(new Set<number>())
    const {togglePaid, contributionPeriodChanged} = usePaidToggle(paidUserIds)

    await contributionPeriodChanged({id: 5, startDate: "2025-01-01", endDate: "2025-12-31"})
    await togglePaid(1)

    // After rollback, user should not be paid
    expect(paidUserIds.value.has(1)).toBe(false)
  })

  it("rollback on deleteContribution error restores user to paidUserIds", async () => {
    mockDeleteContribution.mockRejectedValue(new Error("Network error"))

    const paidUserIds = ref(new Set<number>())
    const {togglePaid, contributionPeriodChanged} = usePaidToggle(paidUserIds)

    await contributionPeriodChanged({id: 5, startDate: "2025-01-01", endDate: "2025-12-31"})
    paidUserIds.value = new Set([1])

    await togglePaid(1)

    // After rollback, user should be restored to paid
    expect(paidUserIds.value.has(1)).toBe(true)
  })

  it("isSaving returns false after toggle completes", async () => {
    const paidUserIds = ref(new Set<number>())
    const {togglePaid, isSaving, contributionPeriodChanged} = usePaidToggle(paidUserIds)

    await contributionPeriodChanged({id: 5, startDate: "2025-01-01", endDate: "2025-12-31"})
    await togglePaid(1)

    expect(isSaving(1)).toBe(false)
  })

  it("isSaving returns true while toggle is in flight", async () => {
    let resolveFn!: () => void
    mockCreateContribution.mockReturnValue(new Promise<{data: object}>((resolve) => {
      resolveFn = () => resolve({data: {}})
    }))

    const paidUserIds = ref(new Set<number>())
    const {togglePaid, isSaving, contributionPeriodChanged} = usePaidToggle(paidUserIds)

    await contributionPeriodChanged({id: 5, startDate: "2025-01-01", endDate: "2025-12-31"})

    const togglePromise = togglePaid(1)
    expect(isSaving(1)).toBe(true)

    resolveFn()
    await togglePromise
    expect(isSaving(1)).toBe(false)
  })

  it("selectedPeriod is set to the period after contributionPeriodChanged", async () => {
    const paidUserIds = ref(new Set<number>())
    const {selectedPeriod, contributionPeriodChanged} = usePaidToggle(paidUserIds)

    expect(selectedPeriod.value).toBeNull()

    const period = {id: 7, startDate: "2025-01-01", endDate: "2025-12-31"}
    await contributionPeriodChanged(period)
    expect(selectedPeriod.value).toEqual(period)
  })

  it("selectedPeriod is null after contributionPeriodChanged with undefined", async () => {
    const paidUserIds = ref(new Set<number>())
    const {selectedPeriod, contributionPeriodChanged} = usePaidToggle(paidUserIds)

    await contributionPeriodChanged({id: 7, startDate: "2025-01-01", endDate: "2025-12-31"})
    expect(selectedPeriod.value).not.toBeNull()

    await contributionPeriodChanged(undefined)
    expect(selectedPeriod.value).toBeNull()
  })
})
