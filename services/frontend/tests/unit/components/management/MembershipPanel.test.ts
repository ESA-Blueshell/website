import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import MembershipPanel from "@/components/management/MembershipPanel.vue"
import {MemberType} from "@/services/api"
import {settle} from "../../pages/helpers"

// ── Hoisted mocks ─────────────────────────────────────────────────────────────

const {
  mockListMembershipsFor,
  mockListDeletedMembershipsFor,
  mockEndOneMembership,
  mockReopenOneMembership,
  mockDeleteOneMembership,
  mockRestoreOneMembership,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockListMembershipsFor: vi.fn(),
  mockListDeletedMembershipsFor: vi.fn(),
  mockEndOneMembership: vi.fn(),
  mockReopenOneMembership: vi.fn(),
  mockDeleteOneMembership: vi.fn(),
  mockRestoreOneMembership: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/domains/user", async () => {
  const {IncassoStanding, MemberType} = await import("@/services/api")
  return {
    IncassoStanding,
    MemberType,
    listMembershipsFor: mockListMembershipsFor,
    listDeletedMembershipsFor: mockListDeletedMembershipsFor,
    endOneMembership: mockEndOneMembership,
    reopenOneMembership: mockReopenOneMembership,
    deleteOneMembership: mockDeleteOneMembership,
    restoreOneMembership: mockRestoreOneMembership,
  }
})

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

// The form's own saving is tested in MembershipFields.test.ts.
vi.mock("@/components/management/MembershipFields.vue", () => ({
  default: {
    name: "MembershipFields",
    props: ["userId", "membership", "submitText", "submitTestid"],
    emits: ["saved"],
    template: "<div class='membership-fields-stub' />",
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

function mountDialog(props: {userId?: number; isAdmin?: boolean} = {}) {
  mockStore.getters.isAdmin = props.isAdmin ?? false
  return mount(MembershipPanel, {
    props: {
      userId: props.userId ?? 42,
    },
  })
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("MembershipPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockListMembershipsFor.mockResolvedValue([])
    mockListDeletedMembershipsFor.mockResolvedValue([])
    mockEndOneMembership.mockResolvedValue(undefined)
    mockReopenOneMembership.mockResolvedValue(undefined)
    mockDeleteOneMembership.mockResolvedValue(undefined)
    mockRestoreOneMembership.mockResolvedValue(undefined)
  })

  it("loads the memberships of the person it is given", async () => {
    mountDialog()
    await settle()
    expect(mockListMembershipsFor).toHaveBeenCalledWith(42)
  })

  // The newest membership is the one the board acts on, so it is drawn first.
  it("puts the memberships newest first", async () => {
    mockListMembershipsFor.mockResolvedValue([
      makeMembership({id: 1, userId: 42, startDate: "2023-01-01"}),
      makeMembership({id: 2, userId: 42, startDate: "2025-01-01"}),
      makeMembership({id: 3, userId: 42, startDate: "2024-01-01"}),
    ])

    const wrapper = mountDialog()
    await settle()

    expect((wrapper.vm as any).memberships.map((one: {id: number}) => one.id)).toEqual([2, 3, 1])
  })

  it("does NOT call findDeletedMemberships when not admin", async () => {
    mountDialog({isAdmin: false})
    await settle()
    expect(mockListDeletedMembershipsFor).not.toHaveBeenCalled()
  })

  it("calls findDeletedMemberships when admin", async () => {
    mountDialog({isAdmin: true})
    await settle()
    expect(mockListDeletedMembershipsFor).toHaveBeenCalledWith(42)
  })

  it("endMembership calls correct SDK fn and emits changed", async () => {
    const activeMembership = makeMembership({id: 10, userId: 42, startDate: "2025-01-01"})
    mockListMembershipsFor.mockResolvedValue([activeMembership])

    const wrapper = mountDialog()
    await settle()

    await (wrapper.vm as any).onEnd(activeMembership)
    expect(mockEndOneMembership).toHaveBeenCalledWith(10)
    expect(wrapper.emitted("changed")).toBeTruthy()
  })

  it("reopenMembership calls correct SDK fn and emits changed", async () => {
    const endedMembership = makeMembership({id: 20, userId: 42, startDate: "2024-01-01", endDate: "2024-12-31"})
    mockListMembershipsFor.mockResolvedValue([endedMembership])

    const wrapper = mountDialog()
    await settle()

    await (wrapper.vm as any).onReopen(endedMembership)
    expect(mockReopenOneMembership).toHaveBeenCalledWith(20)
    expect(wrapper.emitted("changed")).toBeTruthy()
  })

  it("onDelete opens confirmation dialog and does NOT call deleteMembership immediately", async () => {
    const endedMembership = makeMembership({id: 30, userId: 42, startDate: "2024-01-01", endDate: "2024-12-31"})
    mockListMembershipsFor.mockResolvedValue([endedMembership])

    const wrapper = mountDialog()
    await settle()

    ;(wrapper.vm as any).onDelete(endedMembership)
    // confirmation dialog should be open
    expect((wrapper.vm as any).deleteConfirmOpen).toBe(true)
    expect((wrapper.vm as any).deleteTarget).toEqual(endedMembership)
    // deleteMembership must NOT have been called yet
    expect(mockDeleteOneMembership).not.toHaveBeenCalled()
    expect(wrapper.emitted("changed")).toBeFalsy()
  })

  it("onDeleteConfirmed calls deleteMembership and emits changed", async () => {
    const endedMembership = makeMembership({id: 30, userId: 42, startDate: "2024-01-01", endDate: "2024-12-31"})
    mockListMembershipsFor.mockResolvedValue([endedMembership])

    const wrapper = mountDialog()
    await settle()

    // Simulate the confirmation flow
    ;(wrapper.vm as any).onDelete(endedMembership)
    await (wrapper.vm as any).onDeleteConfirmed()

    expect(mockDeleteOneMembership).toHaveBeenCalledWith(30)
    expect(wrapper.emitted("changed")).toBeTruthy()
  })

  it("restoreMembership calls correct SDK fn and emits changed (admin)", async () => {
    const deletedM = makeMembership({id: 99, userId: 42, startDate: "2024-01-01", endDate: "2024-06-01"})
    mockListDeletedMembershipsFor.mockResolvedValue([deletedM])

    const wrapper = mountDialog({isAdmin: true})
    await settle()

    await (wrapper.vm as any).onRestore(deletedM)
    expect(mockRestoreOneMembership).toHaveBeenCalledWith(99)
    expect(wrapper.emitted("changed")).toBeTruthy()
  })

  it("hasActive is true when any membership has no endDate", async () => {
    const activeMembership = makeMembership({id: 10, userId: 42, startDate: "2025-01-01"})
    mockListMembershipsFor.mockResolvedValue([activeMembership])

    const wrapper = mountDialog()
    await settle()

    expect((wrapper.vm as any).hasActive).toBe(true)
  })

  it("hasActive is false when all memberships have endDates", async () => {
    const endedMembership = makeMembership({id: 20, userId: 42, startDate: "2024-01-01", endDate: "2024-12-31"})
    mockListMembershipsFor.mockResolvedValue([endedMembership])

    const wrapper = mountDialog()
    await settle()

    expect((wrapper.vm as any).hasActive).toBe(false)
  })

  it("add-membership section is hidden when hasActive=true", async () => {
    const activeMembership = makeMembership({id: 50, userId: 42, startDate: "2025-01-01"})
    mockListMembershipsFor.mockResolvedValue([activeMembership])

    const wrapper = mountDialog()
    await settle()

    expect((wrapper.vm as any).hasActive).toBe(true)
    expect(wrapper.find("[data-testid='manage-membership-create']").exists()).toBe(false)
  })

  it("add-membership section is shown when hasActive=false, collapsed by default and folds out on toggle", async () => {
    mockListMembershipsFor.mockResolvedValue([])

    const wrapper = mountDialog()
    await settle()

    expect((wrapper.vm as any).hasActive).toBe(false)
    // The add-membership section (toggle) is available...
    expect(wrapper.find("[data-testid='manage-membership-add-pane']").exists()).toBe(true)
    // ...but the create form is collapsed by default.
    expect((wrapper.vm as any).addOpen).toBe(false)
    expect(wrapper.find("[data-testid='manage-membership-create']").exists()).toBe(false)

    // Folding it out reveals the create form.
    ;(wrapper.vm as any).addOpen = true
    await wrapper.vm.$nextTick()
    expect(wrapper.find("[data-testid='manage-membership-create']").exists()).toBe(true)
  })

  it("memberships is empty and v-list is not shown when no memberships exist", async () => {
    mockListMembershipsFor.mockResolvedValue([])

    const wrapper = mountDialog()
    await settle()

    // When memberships is empty, the membership list should be absent
    expect((wrapper.vm as any).memberships).toHaveLength(0)
    // The v-list (with membership rows) should not be rendered
    expect(wrapper.find("[data-testid^='manage-membership-row-']").exists()).toBe(false)
  })

  it("loads again for another person", async () => {
    const wrapper = mountDialog()
    await settle()

    await wrapper.setProps({userId: 43})
    await settle()
    expect(mockListMembershipsFor).toHaveBeenLastCalledWith(43)
  })

  it("edit pane (manage-membership-edit-pane) appears when toggling inline edit", async () => {
    const m = makeMembership({id: 10, userId: 42, startDate: "2025-01-01"})
    mockListMembershipsFor.mockResolvedValue([m])

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
    mockListMembershipsFor.mockResolvedValue([])

    const wrapper = mountDialog()
    await settle()

    expect(wrapper.find("[data-testid='manage-membership-add-pane']").exists()).toBe(true)
  })

  it("ends, resumes, edits, deletes and restores a membership from its own row", async () => {
    const active = makeMembership({id: 50, userId: 42, startDate: "2025-01-01"})
    const ended = makeMembership({id: 30, userId: 42, startDate: "2023-01-01", endDate: "2023-12-31"})
    const deleted = makeMembership({id: 99, userId: 42, startDate: "2022-01-01", endDate: "2022-06-01"})
    mockListMembershipsFor.mockResolvedValue([active, ended])
    mockListDeletedMembershipsFor.mockResolvedValue([deleted])
    const wrapper = mountDialog({isAdmin: true})
    await settle()

    expect(wrapper.get("[data-testid='manage-membership-row-50']").text()).toContain("1 Jan 2025")
    expect(wrapper.get("[data-testid='manage-membership-row-50']").text()).toContain("Active")
    expect(wrapper.get("[data-testid='manage-membership-row-30']").text()).toContain("31 Dec 2023")

    await wrapper.get("[data-testid='manage-membership-edit-btn-50']").trigger("click")
    const form = wrapper.getComponent({name: "MembershipFields"})
    expect(form.props()).toMatchObject({userId: 42, membership: active, submitTestid: "manage-membership-save-btn-50"})
    expect(wrapper.text()).not.toContain("Incasso")
    mockListMembershipsFor.mockClear()
    form.vm.$emit("saved", active)
    await settle()
    expect(wrapper.find("[data-testid='manage-membership-edit-pane']").exists()).toBe(false)
    expect(mockListMembershipsFor).toHaveBeenCalledWith(42)
    expect(wrapper.emitted("changed")).toHaveLength(1)

    await wrapper.get("[data-testid='manage-membership-end-btn-50']").trigger("click")
    await settle()
    expect(mockEndOneMembership).toHaveBeenCalled()
    await wrapper.get("[data-testid='manage-membership-reopen-btn-30']").trigger("click")
    await wrapper.get("[data-testid='manage-membership-restore-btn-99']").trigger("click")
    await settle()
    expect(mockRestoreOneMembership).toHaveBeenCalledWith(99)

    await wrapper.get("[data-testid='manage-membership-delete-btn-30']").trigger("click")
    const dialog = wrapper.getComponent({name: "ConfirmDialog"})
    expect(dialog.props("open")).toBe(true)
    dialog.vm.$emit("update:open", false)
    await settle()
    expect(dialog.props("open")).toBe(false)
  })

  it("resumes an ended membership from its row while none is running", async () => {
    mockListMembershipsFor.mockResolvedValue([makeMembership({id: 30, userId: 42, startDate: "2023-01-01", endDate: "2023-12-31"})])
    const wrapper = mountDialog()
    await settle()

    await wrapper.get("[data-testid='manage-membership-reopen-btn-30']").trigger("click")
    await settle()
    expect(mockReopenOneMembership).toHaveBeenCalled()
  })

  it("folds the add form out from its button, and closes it once a membership is added", async () => {
    mockListMembershipsFor.mockResolvedValue([])
    const wrapper = mountDialog()
    await settle()

    expect(wrapper.get("[data-testid='manage-membership-empty']").text()).toContain("No memberships yet")
    await wrapper.get("[data-testid='manage-membership-add-toggle']").trigger("click")
    await wrapper.get("[data-testid='manage-membership-add-toggle']").trigger("click")
    expect(wrapper.find("[data-testid='manage-membership-create']").exists()).toBe(false)

    await wrapper.get("[data-testid='manage-membership-add-toggle']").trigger("click")
    const form = wrapper.getComponent({name: "MembershipFields"})
    expect(form.props()).toMatchObject({userId: 42, membership: undefined, submitTestid: "manage-membership-create-btn"})
    mockListMembershipsFor.mockClear()
    form.vm.$emit("saved", makeMembership({id: 7, userId: 42, startDate: "2026-01-01"}))
    await settle()

    expect(wrapper.find("[data-testid='manage-membership-create']").exists()).toBe(false)
    expect(mockListMembershipsFor).toHaveBeenCalledWith(42)
    expect(wrapper.emitted("changed")).toHaveLength(1)
  })
})
