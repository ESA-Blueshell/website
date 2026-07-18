/**
 * MemberManagerSelectionPerf.test.ts
 *
 * Performance regression guard: toggling one row's selection must NOT cause
 * sibling rows to re-render.
 *
 * RED before extraction: all rows share the same `selectedIds` ref, so any
 * toggle replaces the entire Set and invalidates every row's `isSelected()`
 * binding — all N rows re-patch.
 *
 * GREEN after extraction: each row is a memoized `MemberManagerRow` /
 * `MemberManagerMobileRow` child component whose `selected` prop only changes
 * for the toggled row; Vue skips patching siblings whose props are unchanged.
 *
 * Detection mechanism:
 *   Each row component exposes `__updateCount` (a ref incremented in
 *   `onUpdated`). The test:
 *     1. Mounts MemberManager with 3 members.
 *     2. Pre-selects member id=3 (Carol) so `hasSelection` / `selectionActive`
 *        is already stable at `true` before the timing window.
 *     3. Snapshots each row component's `__updateCount`.
 *     4. Toggles member id=1 (Alice) by emitting the event on her row component.
 *     5. Awaits nextTick and asserts:
 *          • Alice's row updated (her `selected` prop changed).
 *          • Bob and Carol's rows did NOT update (their props were unchanged).
 *
 *   The pre-selection step (2) is essential: without it the first real toggle
 *   would flip `hasSelection` false→true, updating ALL rows' `selectionActive`
 *   prop — a legitimate update unrelated to the bug.
 */

import {describe, it, expect, vi, beforeEach} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {nextTick} from "vue"
import MemberManager from "@/pages/management/MemberManager.vue"
import MemberManagerRow from "@/components/common/rows/MemberManagerRow.vue"
import MemberManagerMobileRow from "@/components/common/rows/MemberManagerMobileRow.vue"
import {MemberType} from "@/services/api"

// ── Hoisted mocks ─────────────────────────────────────────────────────────────

const {
  mockFindUsers,
  mockFindMemberships,
  mockFindUserById,
  mockFindContributionsByPeriodId,
  mockDeleteUserById,
  mockLgAndUp,
} = vi.hoisted(() => ({
  mockFindUsers: vi.fn(),
  mockFindMemberships: vi.fn(),
  mockFindUserById: vi.fn(),
  mockFindContributionsByPeriodId: vi.fn(),
  mockDeleteUserById: vi.fn(),
  // Manually shaped as a Vue ref so the template auto-unwraps it and
  // v-if="lgAndUp" resolves to the boolean .value, not the object.
  mockLgAndUp: {value: true, __v_isRef: true} as {value: boolean; __v_isRef: true},
}))

vi.mock("vuetify", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vuetify")>()
  return {
    ...(actual as Record<string, unknown>),
    useDisplay: () => ({lgAndUp: mockLgAndUp}),
  }
})

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {
    ...actual,
    findUsers: mockFindUsers,
    findMemberships: mockFindMemberships,
    findUserById: mockFindUserById,
    findContributionsByPeriodId: mockFindContributionsByPeriodId,
    deleteUserById: mockDeleteUserById,
  }
})

// Stub out heavy children that are not relevant to this test
vi.mock("@/components/common/lists/ContributionPeriodList.vue", () => ({
  default: {name: "ContributionPeriodList", template: "<div />"},
}))
vi.mock("@/components/common/banners/TopBanner.vue", () => ({
  default: {name: "TopBanner", template: "<div />"},
}))
vi.mock("@/components/common/modals/DeletionConfirmationDialog.vue", () => ({
  default: {name: "DeletionConfirmationDialog", template: "<div />"},
}))
vi.mock("@/components/common/modals/ManageMembershipDialog.vue", () => ({
  default: {name: "ManageMembershipDialog", template: "<div />"},
}))
// The six per-action bulk dialogs are stubbed — this perf test only cares about the
// member table/selection, not the dialog internals.
vi.mock("@/components/common/modals/bulk/MarkPaidDialog.vue", () => ({
  default: {name: "MarkPaidDialog", template: "<div />"},
}))
vi.mock("@/components/common/modals/bulk/MarkUnpaidDialog.vue", () => ({
  default: {name: "MarkUnpaidDialog", template: "<div />"},
}))
vi.mock("@/components/common/modals/bulk/EndMembershipDialog.vue", () => ({
  default: {name: "EndMembershipDialog", template: "<div />"},
}))
vi.mock("@/components/common/modals/bulk/ResumeMembershipDialog.vue", () => ({
  default: {name: "ResumeMembershipDialog", template: "<div />"},
}))
vi.mock("@/components/common/modals/bulk/ReminderDialog.vue", () => ({
  default: {name: "ReminderDialog", template: "<div />"},
}))
vi.mock("@/components/common/modals/bulk/IncassoDialog.vue", () => ({
  default: {name: "IncassoDialog", template: "<div />"},
}))
vi.mock("@/components/common/modals/BaseModal.vue", () => ({
  default: {name: "BaseModal", template: "<div><slot /></div>"},
}))
vi.mock("@/components/form/UserForm.vue", () => ({
  default: {name: "UserForm", template: "<div />"},
}))
vi.mock("@/components/common/BulkActionsMenu.vue", () => ({
  default: {name: "BulkActionsMenu", template: "<div />"},
}))

// ── Fixtures ──────────────────────────────────────────────────────────────────

function makeUserResponse(id: number, name: string, username: string) {
  return {
    id,
    fullName: name,
    username,
    roles: ["MEMBER"],
    email: `${username}@test.com`,
    enabled: true,
    firstName: name.split(" ")[0] ?? "",
    lastName: name.split(" ")[1] ?? "",
    initials: "XX",
    newsletter: false,
    photoConsent: false,
    createdAt: "2025-01-01T00:00:00.000Z",
    updatedAt: "2025-01-01T00:00:00.000Z",
    version: 0,
  }
}

function makeMembership(id: number, userId: number) {
  return {
    id,
    userId,
    startDate: "2024-01-01",
    endDate: undefined,
    memberType: MemberType.REGULAR,
    incasso: false,
    version: 1,
    createdAt: "2025-01-01T00:00:00.000Z",
    updatedAt: "2025-01-01T00:00:00.000Z",
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Mount MemberManager, wait for data, and pre-select Carol (id=3) so that
 * `hasSelection` (= `selectionActive`) is already `true` before the measurement
 * window. This prevents the first real toggle from flipping `selectionActive`
 * from false→true on ALL sibling rows — that would be a legitimate prop change
 * unrelated to the bug under test.
 *
 * Pre-selection is done by emitting through Carol's row component, then
 * waiting an extra tick so all resulting component updates settle before
 * we snapshot update-counters.
 */
async function mountWithPreselect(lgAndUp: boolean, RowComponent: typeof MemberManagerRow | typeof MemberManagerMobileRow) {
  mockLgAndUp.value = lgAndUp
  const wrapper = mount(MemberManager)
  await flushPromises()
  await nextTick()

  // Pre-select Carol (id=3) via her row component so hasSelection turns true.
  const rowWrappers = wrapper.findAllComponents(RowComponent)
  const carolRow = rowWrappers.find(
    (rw) => (rw.props() as {row: {id: number}}).row.id === 3,
  )
  expect(carolRow).toBeDefined()
  carolRow!.vm.$emit("toggle-selection", 3)
  // Wait multiple ticks so ALL resulting component updates (including sibling
  // selectionActive prop flips) settle before we snapshot counters.
  await nextTick()
  await nextTick()

  return wrapper
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("MemberManager selection perf — isolated row re-render", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindUsers.mockResolvedValue({
      status: 200,
      data: {
        content: [
          makeUserResponse(1, "Alice Smith", "alice"),
          makeUserResponse(2, "Bob Jones", "bob"),
          makeUserResponse(3, "Carol White", "carol"),
        ],
      },
    })
    mockFindMemberships.mockResolvedValue({
      data: [
        makeMembership(10, 1),
        makeMembership(11, 2),
        makeMembership(12, 3),
      ],
    })
    mockFindContributionsByPeriodId.mockResolvedValue({data: []})
    mockFindUserById.mockResolvedValue({
      data: makeUserResponse(1, "Alice Smith", "alice"),
    })
    mockDeleteUserById.mockResolvedValue({})
  })

  it("toggling one row selection does not re-render sibling rows (desktop)", async () => {
    const wrapper = await mountWithPreselect(true, MemberManagerRow)

    // Find all desktop row component instances
    const rowWrappers = wrapper.findAllComponents(MemberManagerRow)
    expect(rowWrappers.length).toBeGreaterThanOrEqual(3)

    // Snapshot update counts (after pre-select stabilisation)
    const countsBefore = rowWrappers.map((rw) => ({
      id: (rw.props() as {row: {id: number}}).row.id,
      count: (rw.vm as unknown as {__updateCount: number}).__updateCount,
    }))

    // Toggle row for member id=1 (Alice) — Carol stays selected, so
    // `selectionActive` remains true and does NOT change for any sibling.
    const aliceRowWrapper = rowWrappers.find(
      (rw) => (rw.props() as {row: {id: number}}).row.id === 1,
    )
    expect(aliceRowWrapper).toBeDefined()
    // Emit the event as if the user clicked Alice's checkbox
    aliceRowWrapper!.vm.$emit("toggle-selection", 1)
    await nextTick()

    // Snapshot update counts after the toggle
    const countsAfter = rowWrappers.map((rw) => ({
      id: (rw.props() as {row: {id: number}}).row.id,
      count: (rw.vm as unknown as {__updateCount: number}).__updateCount,
    }))

    // The toggled row (Alice) must have updated
    const aliceBefore = countsBefore.find((c) => c.id === 1)!.count
    const aliceAfter = countsAfter.find((c) => c.id === 1)!.count
    expect(aliceAfter).toBeGreaterThan(aliceBefore)

    // Sibling rows (Bob id=2, Carol id=3) must NOT have been re-rendered
    for (const {id, count} of countsBefore.filter((c) => c.id !== 1)) {
      const afterCount = countsAfter.find((c) => c.id === id)!.count
      expect(afterCount).toBe(count)
    }
  })

  // NOTE: Mobile row selection was removed in #454 (mobile-only cleanup).
  // This test is skipped since MemberManagerMobileRow no longer supports the
  // toggle-selection event or selection-related behavior.
  // Desktop selection performance testing (above) remains valid.
  it.skip("toggling one row selection does not re-render sibling rows (mobile)", async () => {
    const wrapper = await mountWithPreselect(false, MemberManagerMobileRow)

    const rowWrappers = wrapper.findAllComponents(MemberManagerMobileRow)
    expect(rowWrappers.length).toBeGreaterThanOrEqual(3)

    const countsBefore = rowWrappers.map((rw) => ({
      id: (rw.props() as {row: {id: number}}).row.id,
      count: (rw.vm as unknown as {__updateCount: number}).__updateCount,
    }))

    // Toggle row for member id=1 (Alice)
    const aliceRowWrapper = rowWrappers.find(
      (rw) => (rw.props() as {row: {id: number}}).row.id === 1,
    )
    expect(aliceRowWrapper).toBeDefined()
    aliceRowWrapper!.vm.$emit("toggle-selection", 1)
    await nextTick()

    const countsAfter = rowWrappers.map((rw) => ({
      id: (rw.props() as {row: {id: number}}).row.id,
      count: (rw.vm as unknown as {__updateCount: number}).__updateCount,
    }))

    const aliceBefore = countsBefore.find((c) => c.id === 1)!.count
    const aliceAfter = countsAfter.find((c) => c.id === 1)!.count
    expect(aliceAfter).toBeGreaterThan(aliceBefore)

    for (const {id, count} of countsBefore.filter((c) => c.id !== 1)) {
      const afterCount = countsAfter.find((c) => c.id === id)!.count
      expect(afterCount).toBe(count)
    }
  })
})
