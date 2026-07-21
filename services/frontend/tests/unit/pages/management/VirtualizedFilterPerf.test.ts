/**
 * Regression guard for the UserManager virtualized table (v-data-table-virtual).
 *
 * The table once rendered ALL filtered rows: at 1500 users a search-clear
 * patched 1500 rows x ~35 Vuetify components (~1.8s measured in jsdom).
 * v-data-table-virtual must keep the rendered row count bounded by the
 * viewport, independent of the dataset size.
 *
 * Unlike the rest of the unit suite (which stubs VDataTableVirtual — see
 * tests/setup.ts), this file installs the REAL Vuetify plugin so real
 * virtualization runs. In jsdom, Vuetify's virtual composable falls back to
 * the numeric `:height` prop for the viewport height, so the math works
 * without a layout engine.
 *
 * Timings are logged for humans but NOT asserted (CI machines vary); the
 * assertion is the rendered-row bound, which is what actually pins the
 * performance class of filter/search updates.
 */
import {describe, it, expect, vi, beforeEach} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {nextTick} from "vue"
import {createVuetify} from "vuetify"
import * as vuetifyComponents from "vuetify/components"
import UserManager from "@/pages/management/UserManager.vue"
import UserManagerRow from "@/components/common/rows/UserManagerRow.vue"
import {MemberType} from "@/services/api"

const N = 1500
/** Viewport 600px / 36px rows ≈ 17 visible; Vuetify adds buffer chunks. */
const MAX_RENDERED_ROWS = 80

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

function makeUserResponse(id: number) {
  const username = `user${String(id).padStart(4, "0")}`
  return {
    id,
    fullName: `First${id} Last${id}`,
    username,
    roles: ["MEMBER"],
    email: `${username}@test.com`,
    enabled: true,
    firstName: `First${id}`,
    lastName: `Last${id}`,
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

describe("UserManager virtualized filter perf", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // vi.clearAllMocks wipes the matchMedia implementation installed by
    // tests/setup.ts; createVuetify's display service needs it back.
    globalThis.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })) as unknown as typeof globalThis.matchMedia
    const users = Array.from({length: N}, (_, i) => makeUserResponse(i + 1))
    mockFindUsers.mockResolvedValue({status: 200, data: {content: users}})
    mockFindMemberships.mockResolvedValue({
      data: users.map((u, i) => makeMembership(10000 + i, u.id)),
    })
    mockFindContributionsByPeriodId.mockResolvedValue({data: []})
    mockFindUserById.mockResolvedValue({data: users[0]})
    mockDeleteUserById.mockResolvedValue({})
  })

  it(`renders a bounded number of rows regardless of dataset size (N=${N})`, async () => {
    const vuetify = createVuetify({components: vuetifyComponents})
    // Some Vuetify components need a layout context; wrap the page in v-app.
    const Host = {
      components: {UserManager},
      template: "<v-app><user-manager /></v-app>",
    }
    const t0 = performance.now()
    const wrapper = mount(Host, {
      global: {
        plugins: [vuetify],
        // Use the REAL VDataTableVirtual here, overriding the suite-wide stub.
        components: {VDataTableVirtual: vuetifyComponents.VDataTableVirtual},
      },
    })
    await flushPromises()
    await nextTick()
    const tMount = performance.now() - t0

    const vm = wrapper.findComponent(UserManager).vm as unknown as {search: string; filteredRows: unknown[]}
    expect(vm.filteredRows.length).toBe(N)

    // The core guarantee: rendered rows are bounded by the viewport, not N.
    const mounted = wrapper.findAllComponents(UserManagerRow).length
    expect(mounted).toBeGreaterThan(0)
    expect(mounted).toBeLessThanOrEqual(MAX_RENDERED_ROWS)

    // Narrow (N -> 1) and widen (1 -> N) must both stay bounded.
    const n0 = performance.now()
    vm.search = "user0012"
    await nextTick()
    const tNarrow = performance.now() - n0
    expect(vm.filteredRows.length).toBe(1)

    const w0 = performance.now()
    vm.search = ""
    await nextTick()
    const tWiden = performance.now() - w0
    expect(vm.filteredRows.length).toBe(N)
    expect(wrapper.findAllComponents(UserManagerRow).length).toBeLessThanOrEqual(MAX_RENDERED_ROWS)

    console.log(
      `N=${N}  mount=${tMount.toFixed(0)}ms  renderedRows=${mounted}  ` +
      `narrow=${tNarrow.toFixed(0)}ms  widen=${tWiden.toFixed(0)}ms`,
    )
    wrapper.unmount()
  }, 300_000)
})
