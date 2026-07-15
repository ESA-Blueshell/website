import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import ManageMembershipDialog from "@/components/common/modals/ManageMembershipDialog.vue"
import {MemberType} from "@/services/api"
import {settle} from "../../../pages/helpers"

// ── Hoisted mocks ─────────────────────────────────────────────────────────────

const {
  mockFindMemberships,
  mockFindDeletedMemberships,
  mockEndMembership,
  mockReopenMembership,
  mockDeleteMembership,
  mockRestoreMembership,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockFindMemberships: vi.fn(),
  mockFindDeletedMemberships: vi.fn(),
  mockEndMembership: vi.fn(),
  mockReopenMembership: vi.fn(),
  mockDeleteMembership: vi.fn(),
  mockRestoreMembership: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {
    ...actual,
    findMemberships: mockFindMemberships,
    findDeletedMemberships: mockFindDeletedMemberships,
    endMembership: mockEndMembership,
    reopenMembership: mockReopenMembership,
    deleteMembership: mockDeleteMembership,
    restoreMembership: mockRestoreMembership,
  }
})

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

// Stub MembershipForm — create/update API calls are tested in MembershipForm.test.ts
vi.mock("@/components/form/MembershipForm.vue", () => ({
  default: {
    name: "MembershipForm",
    props: ["modelValue", "userId", "submitTestId", "showSubmit", "submitText"],
    emits: ["submitted", "update:modelValue"],
    template: "<div class='membership-form-stub' />",
  },
}))

// ── Mock store ────────────────────────────────────────────────────────────────

const mockStore = {
  getters: {
    isAdmin: false,
  },
}

vi.mock("vuex", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vuex")>()
  return {
    ...(actual as Record<string, unknown>),
    useStore: () => mockStore,
  }
})

// ── Membership factory ────────────────────────────────────────────────────────

function makeMembership(overrides: {
  id: number
  userId: number
  startDate: string
  endDate?: string
  memberType?: MemberType
  incasso?: boolean
  version?: number
}): import("@/services/api").MembershipResponse {
  return {
    id: overrides.id,
    userId: overrides.userId,
    startDate: overrides.startDate,
    endDate: overrides.endDate,
    memberType: overrides.memberType ?? MemberType.REGULAR,
    incasso: overrides.incasso ?? false,
    version: overrides.version ?? 1,
    createdAt: "2025-01-01T00:00:00.000Z",
    updatedAt: "2025-01-01T00:00:00.000Z",
  }
}

// ── Shared mount helper ───────────────────────────────────────────────────────

function mountDialog(props: {userId?: number; userName?: string; isAdmin?: boolean} = {}) {
  mockStore.getters.isAdmin = props.isAdmin ?? false
  return mount(ManageMembershipDialog, {
    props: {
      modelValue: true,
      userId: props.userId ?? 42,
      userName: props.userName ?? "Alice",
    },
    global: {
      stubs: {
        MembershipForm: true,
      },
    },
  })
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("ManageMembershipDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindMemberships.mockResolvedValue({data: []})
    mockFindDeletedMemberships.mockResolvedValue({data: []})
    mockEndMembership.mockResolvedValue({data: makeMembership({id: 1, userId: 42, startDate: "2025-01-01", endDate: "2025-06-01"})})
    mockReopenMembership.mockResolvedValue({data: makeMembership({id: 1, userId: 42, startDate: "2025-01-01"})})
    mockDeleteMembership.mockResolvedValue({})
    mockRestoreMembership.mockResolvedValue({data: makeMembership({id: 99, userId: 42, startDate: "2024-01-01"})})
  })

  it("loads memberships on open (watch modelValue=true)", async () => {
    mountDialog()
    await settle()
    expect(mockFindMemberships).toHaveBeenCalledWith({query: {userId: 42}})
  })

  it("does NOT call findDeletedMemberships when not admin", async () => {
    mountDialog({isAdmin: false})
    await settle()
    expect(mockFindDeletedMemberships).not.toHaveBeenCalled()
  })

  it("calls findDeletedMemberships when admin", async () => {
    mountDialog({isAdmin: true})
    await settle()
    expect(mockFindDeletedMemberships).toHaveBeenCalledWith({path: {userId: 42}})
  })

  it("endMembership calls correct SDK fn and emits changed", async () => {
    const activeMembership = makeMembership({id: 10, userId: 42, startDate: "2025-01-01"})
    mockFindMemberships.mockResolvedValue({data: [activeMembership]})

    const wrapper = mountDialog()
    await settle()

    await (wrapper.vm as any).onEnd(activeMembership)
    expect(mockEndMembership).toHaveBeenCalledWith({path: {id: 10}, throwOnError: true})
    expect(wrapper.emitted("changed")).toBeTruthy()
  })

  it("reopenMembership calls correct SDK fn and emits changed", async () => {
    const endedMembership = makeMembership({id: 20, userId: 42, startDate: "2024-01-01", endDate: "2024-12-31"})
    mockFindMemberships.mockResolvedValue({data: [endedMembership]})

    const wrapper = mountDialog()
    await settle()

    await (wrapper.vm as any).onReopen(endedMembership)
    expect(mockReopenMembership).toHaveBeenCalledWith({path: {id: 20}, throwOnError: true})
    expect(wrapper.emitted("changed")).toBeTruthy()
  })

  it("onDelete opens confirmation dialog and does NOT call deleteMembership immediately", async () => {
    const endedMembership = makeMembership({id: 30, userId: 42, startDate: "2024-01-01", endDate: "2024-12-31"})
    mockFindMemberships.mockResolvedValue({data: [endedMembership]})

    const wrapper = mountDialog()
    await settle()

    ;(wrapper.vm as any).onDelete(endedMembership)
    // confirmation dialog should be open
    expect((wrapper.vm as any).deleteConfirmOpen).toBe(true)
    expect((wrapper.vm as any).deleteTarget).toEqual(endedMembership)
    // deleteMembership must NOT have been called yet
    expect(mockDeleteMembership).not.toHaveBeenCalled()
    expect(wrapper.emitted("changed")).toBeFalsy()
  })

  it("onDeleteConfirmed calls deleteMembership and emits changed", async () => {
    const endedMembership = makeMembership({id: 30, userId: 42, startDate: "2024-01-01", endDate: "2024-12-31"})
    mockFindMemberships.mockResolvedValue({data: [endedMembership]})

    const wrapper = mountDialog()
    await settle()

    // Simulate the confirmation flow
    ;(wrapper.vm as any).onDelete(endedMembership)
    await (wrapper.vm as any).onDeleteConfirmed()

    expect(mockDeleteMembership).toHaveBeenCalledWith({path: {id: 30}, throwOnError: true})
    expect(wrapper.emitted("changed")).toBeTruthy()
  })

  it("onCreateSubmitted(true) reloads memberships and emits changed", async () => {
    const wrapper = mountDialog()
    await settle()

    vi.clearAllMocks()
    mockFindMemberships.mockResolvedValue({data: []})

    await (wrapper.vm as any).onCreateSubmitted(true)

    expect(mockFindMemberships).toHaveBeenCalledWith({query: {userId: 42}})
    expect(wrapper.emitted("changed")).toBeTruthy()
  })

  it("onCreateSubmitted(false) does NOT reload memberships or emit changed", async () => {
    const wrapper = mountDialog()
    await settle()

    vi.clearAllMocks()

    await (wrapper.vm as any).onCreateSubmitted(false)

    expect(mockFindMemberships).not.toHaveBeenCalled()
    expect(wrapper.emitted("changed")).toBeFalsy()
  })

  it("onEditSubmitted(m, true) closes inline edit, reloads memberships and emits changed", async () => {
    const m = makeMembership({id: 40, userId: 42, startDate: "2025-01-01", version: 3})
    mockFindMemberships.mockResolvedValue({data: [m]})

    const wrapper = mountDialog()
    await settle()

    ;(wrapper.vm as any).toggleInlineEdit(m)
    expect((wrapper.vm as any).editingIds.has(m.id)).toBe(true)

    vi.clearAllMocks()
    mockFindMemberships.mockResolvedValue({data: [m]})

    await (wrapper.vm as any).onEditSubmitted(m, true)

    expect((wrapper.vm as any).editingIds.has(m.id)).toBe(false)
    expect(mockFindMemberships).toHaveBeenCalledWith({query: {userId: 42}})
    expect(wrapper.emitted("changed")).toBeTruthy()
  })

  it("onEditSubmitted(m, false) does NOT close inline edit or emit changed", async () => {
    const m = makeMembership({id: 40, userId: 42, startDate: "2025-01-01", version: 3})
    mockFindMemberships.mockResolvedValue({data: [m]})

    const wrapper = mountDialog()
    await settle()

    ;(wrapper.vm as any).toggleInlineEdit(m)
    vi.clearAllMocks()

    await (wrapper.vm as any).onEditSubmitted(m, false)

    expect((wrapper.vm as any).editingIds.has(m.id)).toBe(true)
    expect(mockFindMemberships).not.toHaveBeenCalled()
    expect(wrapper.emitted("changed")).toBeFalsy()
  })

  it("restoreMembership calls correct SDK fn and emits changed (admin)", async () => {
    const deletedM = makeMembership({id: 99, userId: 42, startDate: "2024-01-01", endDate: "2024-06-01"})
    mockFindDeletedMemberships.mockResolvedValue({data: [deletedM]})

    const wrapper = mountDialog({isAdmin: true})
    await settle()

    await (wrapper.vm as any).onRestore(deletedM)
    expect(mockRestoreMembership).toHaveBeenCalledWith({path: {id: 99}, throwOnError: true})
    expect(wrapper.emitted("changed")).toBeTruthy()
  })

  it("close emits update:modelValue false", async () => {
    const wrapper = mountDialog()
    await settle()

    ;(wrapper.vm as any).close()
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([false])
  })

  it("hasActive is true when any membership has no endDate", async () => {
    const activeMembership = makeMembership({id: 10, userId: 42, startDate: "2025-01-01"})
    mockFindMemberships.mockResolvedValue({data: [activeMembership]})

    const wrapper = mountDialog()
    await settle()

    expect((wrapper.vm as any).hasActive).toBe(true)
  })

  it("hasActive is false when all memberships have endDates", async () => {
    const endedMembership = makeMembership({id: 20, userId: 42, startDate: "2024-01-01", endDate: "2024-12-31"})
    mockFindMemberships.mockResolvedValue({data: [endedMembership]})

    const wrapper = mountDialog()
    await settle()

    expect((wrapper.vm as any).hasActive).toBe(false)
  })

  it("add-membership section is hidden when hasActive=true", async () => {
    const activeMembership = makeMembership({id: 50, userId: 42, startDate: "2025-01-01"})
    mockFindMemberships.mockResolvedValue({data: [activeMembership]})

    const wrapper = mountDialog()
    await settle()

    expect((wrapper.vm as any).hasActive).toBe(true)
    expect(wrapper.find("[data-testid='manage-membership-create']").exists()).toBe(false)
  })

  it("add-membership section is shown when hasActive=false", async () => {
    mockFindMemberships.mockResolvedValue({data: []})

    const wrapper = mountDialog()
    await settle()

    expect((wrapper.vm as any).hasActive).toBe(false)
    expect(wrapper.find("[data-testid='manage-membership-create']").exists()).toBe(true)
  })

  it("memberships is empty and v-list is not shown when no memberships exist", async () => {
    mockFindMemberships.mockResolvedValue({data: []})

    const wrapper = mountDialog()
    await settle()

    // When memberships is empty, the membership list should be absent
    expect((wrapper.vm as any).memberships).toHaveLength(0)
    // The v-list (with membership rows) should not be rendered
    expect(wrapper.find("[data-testid^='manage-membership-row-']").exists()).toBe(false)
  })

  it("title contains 'Manage memberships: Alice' (colon, not em-dash)", async () => {
    const wrapper = mountDialog({userName: "Alice"})
    await settle()

    // The BaseModal receives the title prop — check the computed prop string
    const baseModal = wrapper.findComponent({name: "BaseModal"})
    expect(baseModal.props("title")).toBe("Manage memberships: Alice")
  })

  it("edit pane (manage-membership-edit-pane) appears when toggling inline edit", async () => {
    const m = makeMembership({id: 10, userId: 42, startDate: "2025-01-01"})
    mockFindMemberships.mockResolvedValue({data: [m]})

    const wrapper = mountDialog()
    await settle()

    // Before editing: no edit pane
    expect(wrapper.find("[data-testid='manage-membership-edit-pane']").exists()).toBe(false)

    // Toggle inline edit on membership
    ;(wrapper.vm as any).toggleInlineEdit(m)
    await wrapper.vm.$nextTick()

    expect(wrapper.find("[data-testid='manage-membership-edit-pane']").exists()).toBe(true)
  })

  it("add pane (manage-membership-add-pane) renders when no active membership", async () => {
    mockFindMemberships.mockResolvedValue({data: []})

    const wrapper = mountDialog()
    await settle()

    expect(wrapper.find("[data-testid='manage-membership-add-pane']").exists()).toBe(true)
  })
})
