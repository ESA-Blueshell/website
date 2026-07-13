import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import ManageMembershipDialog from "@/components/common/modals/ManageMembershipDialog.vue"
import {MemberType} from "@/services/api"
import {settle} from "../../../pages/helpers"

// ── Hoisted mocks ─────────────────────────────────────────────────────────────

const {
  mockFindMemberships,
  mockFindDeletedMemberships,
  mockBoardCreateMembership,
  mockEndMembership,
  mockReopenMembership,
  mockDeleteMembership,
  mockUpdateMembership,
  mockRestoreMembership,
  mockApply,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockFindMemberships: vi.fn(),
  mockFindDeletedMemberships: vi.fn(),
  mockBoardCreateMembership: vi.fn(),
  mockEndMembership: vi.fn(),
  mockReopenMembership: vi.fn(),
  mockDeleteMembership: vi.fn(),
  mockUpdateMembership: vi.fn(),
  mockRestoreMembership: vi.fn(),
  mockApply: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {
    ...actual,
    findMemberships: mockFindMemberships,
    findDeletedMemberships: mockFindDeletedMemberships,
    boardCreateMembership: mockBoardCreateMembership,
    endMembership: mockEndMembership,
    reopenMembership: mockReopenMembership,
    deleteMembership: mockDeleteMembership,
    updateMembership: mockUpdateMembership,
    restoreMembership: mockRestoreMembership,
  }
})

vi.mock("@/plugins/validation.ts", () => ({
  apply: mockApply,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

vi.mock("@/components/form/fields/VvField.vue", () => ({
  default: {
    name: "VvField",
    template: "<div />",
  },
}))

vi.mock("@/components/form/fields/MemberTypeSelect.vue", () => ({
  default: {
    name: "MemberTypeSelect",
    template: "<div />",
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
        Form: true,
        VvField: true,
        MemberTypeSelect: true,
      },
    },
  })
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("ManageMembershipDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockApply.mockReturnValue(false)
    mockFindMemberships.mockResolvedValue({data: []})
    mockFindDeletedMemberships.mockResolvedValue({data: []})
    mockBoardCreateMembership.mockResolvedValue({data: makeMembership({id: 1, userId: 42, startDate: "2025-01-01"})})
    mockEndMembership.mockResolvedValue({data: makeMembership({id: 1, userId: 42, startDate: "2025-01-01", endDate: "2025-06-01"})})
    mockReopenMembership.mockResolvedValue({data: makeMembership({id: 1, userId: 42, startDate: "2025-01-01"})})
    mockDeleteMembership.mockResolvedValue({})
    mockUpdateMembership.mockResolvedValue({data: makeMembership({id: 1, userId: 42, startDate: "2025-01-01"})})
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

  it("deleteMembership calls correct SDK fn and emits changed", async () => {
    const endedMembership = makeMembership({id: 30, userId: 42, startDate: "2024-01-01", endDate: "2024-12-31"})
    mockFindMemberships.mockResolvedValue({data: [endedMembership]})

    const wrapper = mountDialog()
    await settle()

    await (wrapper.vm as any).onDelete(endedMembership)
    expect(mockDeleteMembership).toHaveBeenCalledWith({path: {id: 30}, throwOnError: true})
    expect(wrapper.emitted("changed")).toBeTruthy()
  })

  it("boardCreateMembership calls correct SDK fn and emits changed", async () => {
    const wrapper = mountDialog()
    await settle()

    ;(wrapper.vm as any).createForm = {
      startDate: "2025-06-01",
      memberType: MemberType.REGULAR,
      userId: 42,
      incasso: false,
    }
    ;(wrapper.vm as any).createFormRef = {
      validate: vi.fn().mockResolvedValue({valid: true}),
    }

    await (wrapper.vm as any).onCreate()
    expect(mockBoardCreateMembership).toHaveBeenCalledWith({
      path: {userId: 42},
      body: expect.objectContaining({startDate: "2025-06-01", userId: 42}),
      throwOnError: true,
    })
    expect(wrapper.emitted("changed")).toBeTruthy()
  })

  it("updateMembership (correct) sends version and emits changed", async () => {
    const m = makeMembership({id: 40, userId: 42, startDate: "2025-01-01", version: 3})
    mockFindMemberships.mockResolvedValue({data: [m]})

    const wrapper = mountDialog()
    await settle()

    // Start inline edit
    ;(wrapper.vm as any).toggleInlineEdit(m)
    const edit = (wrapper.vm as any).inlineEdits[m.id]
    edit.startDate = "2025-02-01"
    edit.endDate = ""
    edit.memberType = MemberType.REGULAR
    edit.incasso = true
    edit.formRef = undefined

    await (wrapper.vm as any).onSaveCorrect(m)

    expect(mockUpdateMembership).toHaveBeenCalledWith({
      path: {id: 40},
      body: expect.objectContaining({
        startDate: "2025-02-01",
        version: 3,
        userId: 42,
        incasso: true,
      }),
      throwOnError: true,
    })
    expect(wrapper.emitted("changed")).toBeTruthy()
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
})
