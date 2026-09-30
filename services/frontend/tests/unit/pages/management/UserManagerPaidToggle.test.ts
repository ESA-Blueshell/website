import {beforeEach, describe, expect, it, vi} from "vitest"
import UserManager from "@/pages/management/UserManager.vue"
import {MemberType} from "@/services/api"
import {settle} from "../helpers"
import {boardLogin, mountPage} from "../../helpers/mountPage"

const {
  mockFindUsers,
  mockFindUserById,
  mockFindMemberships,
  mockFindContributionsByPeriodId,
  mockDeleteUserById,
  mockCreateContribution,
  mockDeleteContribution,
  mockFindContributionPeriods,
} = vi.hoisted(() => ({
  mockFindUsers: vi.fn(),
  mockFindUserById: vi.fn(),
  mockFindMemberships: vi.fn(),
  mockFindContributionsByPeriodId: vi.fn(),
  mockDeleteUserById: vi.fn(),
  mockCreateContribution: vi.fn(),
  mockDeleteContribution: vi.fn(),
  mockFindContributionPeriods: vi.fn(),
}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {
    ...actual,
    findUsers: mockFindUsers,
    findUserById: mockFindUserById,
    findMemberships: mockFindMemberships,
    findContributionsByPeriodId: mockFindContributionsByPeriodId,
    deleteUserById: mockDeleteUserById,
    createContribution: mockCreateContribution,
    deleteContribution: mockDeleteContribution,
    findContributionPeriods: mockFindContributionPeriods,
  }
})

describe("UserManager paid toggle", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindUsers.mockResolvedValue({
      status: 200,
      data: {
        content: [
          {id: 1, fullName: "Alice Smith", username: "alice", roles: ["MEMBER"], email: "alice@test.com", enabled: true, firstName: "Alice", lastName: "Smith", initials: "AS", newsletter: false, photoConsent: false, createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z", version: 0},
        ],
      },
    })
    mockFindUserById.mockResolvedValue({
      data: {id: 1, fullName: "Alice Smith", username: "alice", roles: ["MEMBER"], email: "alice@test.com", enabled: true, firstName: "Alice", lastName: "Smith", initials: "AS", newsletter: false, photoConsent: false, createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z", version: 0},
    })
    mockFindMemberships.mockResolvedValue({
      data: [{
        id: 90, userId: 1, startDate: "2024-01-01",
        memberType: MemberType.REGULAR, incasso: false,
        version: 1, createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z",
      }],
    })
    mockFindContributionsByPeriodId.mockResolvedValue({data: []})
    mockDeleteUserById.mockResolvedValue({})
    mockCreateContribution.mockResolvedValue({data: {userId: 1, contributionPeriodId: 5, version: 1, createdAt: "", updatedAt: ""}})
    mockDeleteContribution.mockResolvedValue({})
    mockFindContributionPeriods.mockResolvedValue({data: [{id: 5, startDate: "2025-01-01", endDate: "2025-12-31"}]})
  })

  const mount = () => mountPage(UserManager, {path: "/management/users", login: boardLogin, width: 1400})

  // Picked the way a board member picks it, from the list of periods.
  const choosePeriod = async (wrapper: Awaited<ReturnType<typeof mount>>) => {
    await wrapper.get('[data-testid="contribution-period-select-btn-5"]').trigger("click")
    await settle()
  }

  const paidStatus = (wrapper: Awaited<ReturnType<typeof mount>>) =>
    wrapper.get('[data-testid="member-manager-paid-status-1"]').text()

  const toggleButton = (wrapper: Awaited<ReturnType<typeof mount>>) =>
    wrapper.get('[data-testid="member-manager-toggle-paid-btn-1"]')

  it("offers no paid toggle while no period is recorded", async () => {
    mockFindContributionPeriods.mockResolvedValue({data: []})
    const wrapper = await mount()

    expect(toggleButton(wrapper).attributes("disabled")).toBeDefined()
  })

  it("offers the paid toggle on the latest period, which the list picks on arrival", async () => {
    const wrapper = await mount()

    expect(toggleButton(wrapper).attributes("disabled")).toBeUndefined()
  })

  it("marks an unpaid member paid", async () => {
    const wrapper = await mount()
    await choosePeriod(wrapper)
    expect(paidStatus(wrapper)).toBe("Unpaid")

    await toggleButton(wrapper).trigger("click")
    await settle()

    expect(mockCreateContribution).toHaveBeenCalledWith({body: {userId: 1, contributionPeriodId: 5}})
    expect(paidStatus(wrapper)).toBe("Paid")
  })

  it("takes a paid member back to unpaid", async () => {
    mockFindContributionsByPeriodId.mockResolvedValue({data: [{userId: 1, contributionPeriodId: 5}]})
    const wrapper = await mount()
    await choosePeriod(wrapper)
    expect(paidStatus(wrapper)).toBe("Paid")

    await toggleButton(wrapper).trigger("click")
    await settle()

    expect(mockDeleteContribution).toHaveBeenCalledWith({path: {contributionPeriodId: 5, userId: 1}})
    expect(paidStatus(wrapper)).toBe("Unpaid")
  })

  it("reports a failed contribution read instead of rendering every member unpaid", async () => {
    mockFindContributionsByPeriodId.mockResolvedValue({error: {status: 500}, data: undefined})
    const wrapper = await mount()

    await choosePeriod(wrapper)

    expect(paidStatus(wrapper)).toBe("?")
    expect(wrapper.find('[data-testid="member-manager-paid-unknown"]').exists()).toBe(true)
    // The toggle and the bulk contribution actions are both off a set nobody read.
    expect(toggleButton(wrapper).attributes("disabled")).toBeDefined()
  })

  it("a period with no contributions is known to hold none", async () => {
    const wrapper = await mount()

    await choosePeriod(wrapper)

    expect(paidStatus(wrapper)).toBe("Unpaid")
    expect(wrapper.find('[data-testid="member-manager-paid-unknown"]').exists()).toBe(false)
  })

  it("shows no toggle in progress while nothing is being saved", async () => {
    const wrapper = await mount()

    await choosePeriod(wrapper)

    expect(toggleButton(wrapper).classes()).not.toContain("v-btn--loading")
  })
})
