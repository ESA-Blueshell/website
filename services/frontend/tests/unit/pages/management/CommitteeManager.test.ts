import {beforeEach, describe, expect, it, vi} from "vitest"
import CommitteeManager from "@/pages/management/CommitteeManager.vue"
import {mountInApp, settle} from "../helpers"

const {
  mockFindCommittees,
  mockFindUsers,
  mockFindUserById,
  mockDeleteCommitteeById,
  mockUpdateCommittee,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockFindCommittees: vi.fn(),
  mockFindUsers: vi.fn(),
  mockFindUserById: vi.fn(),
  mockDeleteCommitteeById: vi.fn(),
  mockUpdateCommittee: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  findCommittees: mockFindCommittees,
  findUsers: mockFindUsers,
  findUserById: mockFindUserById,
  deleteCommitteeById: mockDeleteCommitteeById,
  updateCommittee: mockUpdateCommittee,
  createCommittee: vi.fn(),
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
  $showStatusMessage: vi.fn(),
}))

vi.mock("vuex", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vuex")>()
  return {...actual, useStore: () => ({getters: {isLoggedIn: true, isBoard: true}})}
})

// The delete action reads `$store.getters.isBoard` from the template, and a template
// reads the global property rather than the composable the rest of the page mocks.
const mountManager = (stubs: Record<string, unknown> = {}) =>
  mountInApp(CommitteeManager, {
    global: {mocks: {$store: {getters: {isBoard: true}}}, stubs},
  })

describe("CommitteeManager page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindCommittees.mockResolvedValue({
      data: [
        {id: 5, name: "Events", description: "desc", version: 1, members: [{userId: 1, role: "MEMBER"}]},
      ],
    })
    mockFindUsers.mockResolvedValue({data: {content: [{id: 1, fullName: "Alice"}], page: {totalElements: 1}}})
    mockFindUserById.mockResolvedValue({data: {id: 1, fullName: "Alice"}})
    mockDeleteCommitteeById.mockResolvedValue({})
    mockUpdateCommittee.mockResolvedValue({
      data: {id: 5, name: "Events Updated", description: "desc", version: 2, members: [{userId: 1, role: "MEMBER"}]},
    })
  })

  it("loads committees and upserts committee updates", async () => {
    const wrapper = mountManager({CommitteeForm: true, DeletionConfirmationDialog: true})

    await settle()

    expect(mockFindCommittees).toHaveBeenCalledTimes(1)
    // The page holds no user list of its own: each picker asks for the term it was typed.
    expect(mockFindUsers).not.toHaveBeenCalled()
    expect((wrapper.vm as any).committees).toHaveLength(1)

    ;(wrapper.vm as any).updateCommittee({id: 5, name: "Events Updated", description: "d", members: []})
    expect((wrapper.vm as any).committees).toHaveLength(1)
    expect((wrapper.vm as any).committees[0].name).toBe("Events Updated")

    ;(wrapper.vm as any).updateCommittee({id: 7, name: "SiteCie", description: "d", members: []})
    expect((wrapper.vm as any).committees).toHaveLength(2)
  })

  it("says the committees could not be read rather than showing the empty-list art", async () => {
    mockFindCommittees.mockRejectedValue({response: {status: 500}})
    const wrapper = mountManager({CommitteeForm: true, DeletionConfirmationDialog: true})

    await settle()

    // Pinned: without throwOnError a 500 resolves, and the empty-list art comes back.
    expect(mockFindCommittees).toHaveBeenCalledWith({throwOnError: true})
    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect((wrapper.vm as any).committeesUnknown).toBe(true)
    expect((wrapper.vm as any).noCommittees).toBe(false)
    expect(wrapper.find('[data-testid="committee-manager-load-failed"]').exists()).toBe(true)
  })

  it("shows the empty-list art where there genuinely are none", async () => {
    mockFindCommittees.mockResolvedValue({data: []})
    const wrapper = mountManager({CommitteeForm: true, DeletionConfirmationDialog: true})

    await settle()

    expect((wrapper.vm as any).noCommittees).toBe(true)
    expect((wrapper.vm as any).committeesUnknown).toBe(false)
    expect(wrapper.find('[data-testid="committee-manager-load-failed"]').exists()).toBe(false)
  })

  it("deletes selected committee", async () => {
    const wrapper = mountManager()
    await settle()

    ;(wrapper.vm as any).committeeToDelete = {id: 5, name: "Events"}
    await (wrapper.vm as any).deleteCommittee()

    expect(mockDeleteCommitteeById).toHaveBeenCalledWith({path: {id: 5}, throwOnError: true})
    expect((wrapper.vm as any).committees).toHaveLength(0)
  })

  it("a refused delete leaves the committee on the page", async () => {
    mockDeleteCommitteeById.mockRejectedValueOnce(new Error("forbidden"))
    const wrapper = mountManager()
    await settle()
    const before = (wrapper.vm as any).committees.length

    ;(wrapper.vm as any).committeeToDelete = {id: 5, name: "Events"}
    await (wrapper.vm as any).deleteCommittee()

    expect((wrapper.vm as any).committees).toHaveLength(before)
    expect(mockDeleteCommitteeById).toHaveBeenCalledWith(
      expect.objectContaining({throwOnError: true}),
    )
  })

  // Driven through the button rather than through `save()`: what refused this save was a
  // rule the form registers, so the form has to be the real one.
  it("saves an edited committee nothing on the page vouches for", async () => {
    const wrapper = mountManager({DeletionConfirmationDialog: true})
    await settle()

    await wrapper.find('[data-testid="committee-edit-btn-5"]').trigger("click")
    await settle()

    const form = wrapper.find('[data-testid="committee-manager-edit-form-5"]')
    await form.findAll("input")[0].setValue("Events Updated")
    await form.find("textarea").setValue("A description long enough to pass")
    await settle()

    await form.find('[data-testid="committee-form-submit-btn"]').trigger("click")
    await settle()

    expect(mockUpdateCommittee).toHaveBeenCalledTimes(1)
    expect(mockUpdateCommittee.mock.calls[0][0].body.name).toBe("Events Updated")
  }, 30_000)
})
