import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {DOMWrapper, flushPromises, mount, type VueWrapper} from "@vue/test-utils"
import FolderCare from "@/domains/cohorts/island/FolderCare.vue"
import {TargetSystem} from "@/services/api"

const api = vi.hoisted(() => ({listTargetFolderStates: vi.fn(), mergeTargetFolders: vi.fn(), removeTargetFolder: vi.fn()}))
const {mockStore} = vi.hoisted(() => ({mockStore: {commit: vi.fn()}}))

vi.mock("@/plugins/store", () => ({default: mockStore}))
vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const folder = (id: string, name: string, targets: number) => ({id, name, targets})
const inBody = (testid: string) => new DOMWrapper(document.body.querySelector(`[data-testid="${testid}"]`)!)

describe("the folders that want a hand", () => {
  const wrappers: VueWrapper[] = []
  const care = async (after: unknown = 1) => {
    const wrapper = mount(FolderCare, {props: {system: TargetSystem.BREVO, after}, attachTo: document.body})
    wrappers.push(wrapper)
    await flushPromises()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.listTargetFolderStates.mockResolvedValue({status: 200, data: [
      folder("3", "Committees", 4), folder("9", "committees", 1), folder("5", "Boards", 2), folder("6", "Old", 0), folder("7", "Older", 0),
    ]})
  })

  afterEach(() => {
    while (wrappers.length > 0) wrappers.pop()!.unmount()
  })

  it("says nothing while every folder has its own name and holds a list", async () => {
    api.listTargetFolderStates.mockResolvedValue({status: 200, data: [folder("3", "Committees", 4), folder("5", "Boards", 2)]})

    expect((await care()).find('[data-testid="folder-care"]').exists()).toBe(false)
  })

  it("merges the folders that share a name once that is confirmed, and reads the folders again", async () => {
    api.mergeTargetFolders.mockResolvedValue({status: 200, data: {removed: 1, moved: 1}})
    const wrapper = await care()

    expect(wrapper.get('[data-testid="folder-care-shared"]').text()).toContain("Two or more folders are called Committees.")
    await wrapper.get('[data-testid="folder-care-merge"]').trigger("click")
    await flushPromises()
    expect(inBody("folder-care-merge-dialog").text()).toContain("2 folders holding 5 lists")
    await inBody("folder-care-merge-confirm").trigger("click")
    await flushPromises()

    expect(api.mergeTargetFolders).toHaveBeenCalledWith({path: {system: "BREVO"}})
    expect(mockStore.commit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", "1 folder removed, 1 list moved.")
    expect(api.listTargetFolderStates).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted("changed")).toHaveLength(1)
  })

  it("keeps the merge open and says why when the system refuses, and counts several shared names", async () => {
    api.listTargetFolderStates.mockResolvedValue({status: 200, data: [
      folder("3", "Committees", 4), folder("9", "Committees", 0), folder("5", "Boards", 2), folder("8", "Boards", 0),
    ]})
    api.mergeTargetFolders.mockResolvedValue({status: 502, error: {message: "Brevo refused."}, response: {status: 502}})
    const wrapper = await care()

    expect(wrapper.get('[data-testid="folder-care-shared"]').text()).toContain("2 folder names are each used by two or more folders.")
    // A folder that shares its name is merged, not offered for removal.
    expect(wrapper.find('[data-testid="folder-care-empty"]').exists()).toBe(false)
    await wrapper.get('[data-testid="folder-care-merge"]').trigger("click")
    await flushPromises()
    await inBody("folder-care-merge-confirm").trigger("click")
    await flushPromises()

    expect(mockStore.commit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", "The folders could not be merged.")
    expect(wrapper.emitted("changed")).toBeUndefined()
    wrapper.findAllComponents({name: "ModalDialog"})[0]!.vm.$emit("update:open", false)
    await flushPromises()
    expect(wrapper.findAllComponents({name: "ModalDialog"})[0]!.props("open")).toBe(false)
  })

  it("removes only the empty folders that are ticked, none of them ticked for the reader", async () => {
    api.removeTargetFolder.mockResolvedValue({status: 204, data: undefined})
    const wrapper = await care()

    expect(wrapper.get('[data-testid="folder-care-empty"]').text()).toContain("2 folders hold no list: Old, Older.")
    await wrapper.get('[data-testid="folder-care-remove"]').trigger("click")
    await flushPromises()
    expect(inBody("folder-care-remove-confirm").attributes("disabled")).toBeDefined()
    await inBody("folder-care-pick-6").setValue(true)
    await inBody("folder-care-pick-7").setValue(true)
    await inBody("folder-care-pick-7").setValue(false)
    expect(inBody("folder-care-remove-confirm").text()).toBe("Remove 1 folder")
    await inBody("folder-care-remove-confirm").trigger("click")
    await flushPromises()

    expect(api.removeTargetFolder).toHaveBeenCalledTimes(1)
    expect(api.removeTargetFolder).toHaveBeenCalledWith({path: {system: "BREVO", folderId: "6"}})
    expect(mockStore.commit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", "1 folder removed.")
    expect(wrapper.emitted("changed")).toHaveLength(1)
  })

  it("says why a folder could not be removed and keeps the dialog, and reads again when the page has", async () => {
    api.removeTargetFolder.mockResolvedValue({status: 502, error: {message: "Brevo refused."}, response: {status: 502}})
    const wrapper = await care()

    await wrapper.get('[data-testid="folder-care-remove"]').trigger("click")
    await flushPromises()
    await inBody("folder-care-remove-confirm").trigger("click")
    expect(api.removeTargetFolder).not.toHaveBeenCalled()
    await inBody("folder-care-pick-6").setValue(true)
    await inBody("folder-care-remove-confirm").trigger("click")
    await flushPromises()

    expect(mockStore.commit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", "The folder could not be removed.")
    expect(wrapper.emitted("changed")).toBeUndefined()
    const dialog = wrapper.findAllComponents({name: "ModalDialog"})[1]!
    expect(dialog.props("open")).toBe(true)
    dialog.vm.$emit("update:open", false)
    await flushPromises()
    expect(dialog.props("open")).toBe(false)

    api.listTargetFolderStates.mockClear()
    await wrapper.setProps({after: 2})
    await flushPromises()
    expect(api.listTargetFolderStates).toHaveBeenCalledTimes(1)
  })
})
