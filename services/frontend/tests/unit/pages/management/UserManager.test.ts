import {beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import UserManager from "@/pages/management/UserManager.vue"
import {
  deleteUserById,
  findContributionPeriods,
  findContributionsByPeriodId,
  findMemberships,
  findUserById,
  findUsers,
  MemberType,
  type MembershipResponse,
  type UserDetailResponse,
} from "@/services/api"
import {answer, emptyAnswer, refusal} from "../../helpers/sdkAnswers"
import {boardLogin, chooseOption, mountPage} from "../../helpers/mountPage"
import {settle} from "../helpers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findUsers: vi.fn(),
  findUserById: vi.fn(),
  findMemberships: vi.fn(),
  findContributionPeriods: vi.fn(),
  findContributionsByPeriodId: vi.fn(),
  deleteUserById: vi.fn(),
}))

function user(id: number, fullName: string, username: string, extra: Partial<UserDetailResponse> = {}): UserDetailResponse {
  const [firstName, lastName] = fullName.split(" ")
  return {
    id, fullName, username, firstName, lastName, initials: "XX", roles: ["MEMBER"], email: `${username}@test.com`,
    enabled: true, newsletter: false, photoConsent: false,
    createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z", version: 0,
    ...extra,
  } as UserDetailResponse
}

function membership(id: number, userId: number, startDate: string, extra: Partial<MembershipResponse> = {}): MembershipResponse {
  return {
    id, userId, startDate, memberType: MemberType.REGULAR, incasso: false, version: 1,
    createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z", ...extra,
  }
}

// Three standings: Alice is a member now, Bob was one, Carol never was.
const alice = user(1, "Alice Smith", "alice", {discordId: "111"})
const bob = user(2, "Bob Jones", "bob")
const carol = user(3, "Carol Adams", "carol")
const memberships = [
  membership(90, 1, "2024-01-01", {incasso: true}),
  membership(91, 2, "2020-01-01", {endDate: "2021-12-31"}),
]

const mount = () => mountPage(UserManager, {path: "/management/users", login: boardLogin, width: 1400})

const rowIds = (wrapper: VueWrapper<any>) =>
  wrapper.findAll('[data-testid^="member-manager-row-"]').map((row) => Number(row.attributes("data-testid")!.split("-").at(-1)))

const status = (wrapper: VueWrapper<any>, id: number) => wrapper.get(`[data-testid="member-manager-status-${id}"]`).text()

async function deleteRow(wrapper: VueWrapper<any>, id: number) {
  await wrapper.get(`[data-testid="member-manager-delete-btn-${id}"]`).trigger("click")
  await settle()
  await wrapper.get('[data-testid="deletion-confirmation-confirm-btn"]').trigger("click")
  await settle()
}

describe("the user manager", () => {
  beforeEach(() => {
    vi.mocked(findUsers).mockResolvedValue(answer(findUsers, {content: [alice, bob, carol]}))
    vi.mocked(findUserById).mockResolvedValue(answer(findUserById, alice))
    vi.mocked(findMemberships).mockResolvedValue(answer(findMemberships, memberships))
    vi.mocked(findContributionPeriods).mockResolvedValue(
      answer(findContributionPeriods, [{id: 8, startDate: "2024-01-01", endDate: "2024-12-31", halfYearCutoffDate: "2024-07-01", halfYearFee: 0, fullYearFee: 0, alumniFee: 0, version: 0, createdAt: "", updatedAt: ""}]),
    )
    vi.mocked(findContributionsByPeriodId).mockResolvedValue(
      answer(findContributionsByPeriodId, [{userId: 1, contributionPeriodId: 8, version: 0, createdAt: "", updatedAt: ""}]),
    )
    vi.mocked(deleteUserById).mockResolvedValue(emptyAnswer(deleteUserById))
  })

  it("lists every account the api holds, in the order it answered them", async () => {
    const wrapper = await mount()

    expect(rowIds(wrapper)).toEqual([1, 2, 3])
  })

  it("says whether each is a member now, was one, or never was", async () => {
    const wrapper = await mount()

    expect(status(wrapper, 1)).toContain("Current")
    expect(status(wrapper, 2)).toContain("Former")
    expect(status(wrapper, 3)).toContain("Never")
  })

  it("narrows the list to the accounts matching what is typed", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="member-manager-search-input"] input').setValue("bob")

    await vi.waitFor(() => expect(rowIds(wrapper)).toEqual([2]))
  })

  it("sorts by name once the name column is chosen, and turns the order round on a second press", async () => {
    vi.mocked(findUsers).mockResolvedValue(answer(findUsers, {content: [bob, carol, alice]}))
    const wrapper = await mount()

    await wrapper.get('[data-testid="member-manager-header-name"]').trigger("click")
    await settle()
    expect(rowIds(wrapper)).toEqual([1, 2, 3])

    await wrapper.get('[data-testid="member-manager-header-name"]').trigger("click")
    await settle()
    expect(rowIds(wrapper)).toEqual([3, 2, 1])
  })

  it("sorts members now before former members before those who never were", async () => {
    vi.mocked(findUsers).mockResolvedValue(answer(findUsers, {content: [carol, bob, alice]}))
    const wrapper = await mount()

    await wrapper.get('[data-testid="member-manager-header-status"]').trigger("click")
    await settle()

    expect(rowIds(wrapper)).toEqual([1, 2, 3])
  })

  it("shows only members, or only non-members, by the membership filter", async () => {
    const wrapper = await mount()

    await chooseOption(wrapper, "member-manager-filter-membership", "Yes")
    expect(rowIds(wrapper)).toEqual([1])
    await chooseOption(wrapper, "member-manager-filter-membership", "No")
    expect(rowIds(wrapper)).toEqual([2, 3])
  })

  it("shows only those who paid in the period, or only those who did not", async () => {
    const wrapper = await mount()

    await chooseOption(wrapper, "member-manager-filter-paid", "Yes")
    expect(rowIds(wrapper)).toEqual([1])
    await chooseOption(wrapper, "member-manager-filter-paid", "No")
    expect(rowIds(wrapper)).toEqual([2, 3])
  })

  it("shows only those paying by incasso, or only those who are not", async () => {
    const wrapper = await mount()

    await chooseOption(wrapper, "member-manager-filter-incasso", "Yes")
    expect(rowIds(wrapper)).toEqual([1])
    await chooseOption(wrapper, "member-manager-filter-incasso", "No")
    expect(rowIds(wrapper)).toEqual([2, 3])
  })

  it("shows only those who were members in the chosen period, or only those who were not", async () => {
    const wrapper = await mount()

    await chooseOption(wrapper, "member-manager-filter-period-member", "Yes")
    expect(rowIds(wrapper)).toEqual([1])
    await chooseOption(wrapper, "member-manager-filter-period-member", "No")
    expect(rowIds(wrapper)).toEqual([2, 3])
  })

  it("shows only the accounts with no Discord member linked", async () => {
    const wrapper = await mount()

    await chooseOption(wrapper, "member-manager-filter-discord", "No")

    expect(rowIds(wrapper)).toEqual([2, 3])
  })

  it("reads the account afresh before offering to edit it", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="member-manager-edit-profile-btn-1"]').trigger("click")
    await settle()

    expect(findUserById).toHaveBeenCalledWith({path: {userId: 1}})
    expect(wrapper.find('[data-testid="member-manager-edit-profile-dialog"]').exists()).toBe(true)
  })

  it("opens a member's memberships, listing the ones on file", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="member-manager-manage-membership-btn-1"]').trigger("click")
    await settle()

    expect(wrapper.find('[data-testid="manage-membership-dialog"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="manage-membership-row-90"]').exists()).toBe(true)
  })

  it("deletes an account once the deletion is confirmed, and takes its row off the list", async () => {
    const wrapper = await mount()

    await deleteRow(wrapper, 2)

    expect(deleteUserById).toHaveBeenCalledWith({path: {userId: 2}, throwOnError: true})
    expect(rowIds(wrapper)).toEqual([1, 3])
  })

  it("keeps the account on the list when the api refuses to delete it", async () => {
    vi.mocked(deleteUserById).mockRejectedValue(refusal(deleteUserById, null, 409))
    const wrapper = await mount()

    await deleteRow(wrapper, 2)

    expect(rowIds(wrapper)).toEqual([1, 2, 3])
  })
})
