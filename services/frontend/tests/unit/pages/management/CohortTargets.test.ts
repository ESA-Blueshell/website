import {describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import CohortTargets from "@/pages/management/CohortTargets.vue"
import ManagerCard from "@/components/common/cards/ManagerCard.vue"
import BaseModal from "@/components/common/modals/BaseModal.vue"
import {
  applyTidy,
  archiveTarget,
  createFolderInSystem,
  createListInSystem,
  deleteTarget,
  fetchTargetDescriptors,
  fetchTargetFolders,
  fetchTargetOptions,
  fetchTidyPlan,
  renameTarget,
} from "@/domains/cohorts/adapters/cohorts"
import {mountInApp} from "../helpers"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  fetchTargetDescriptors: vi.fn(),
  fetchTargetOptions: vi.fn(),
  fetchTargetFolders: vi.fn(),
  createListInSystem: vi.fn(),
  createFolderInSystem: vi.fn(),
  renameTarget: vi.fn(),
  archiveTarget: vi.fn(),
  deleteTarget: vi.fn(),
  fetchTidyPlan: vi.fn(),
  applyTidy: vi.fn(),
}))

const modal = (wrapper: Awaited<ReturnType<typeof mountPage>>, testid: string) =>
  wrapper.findAllComponents(BaseModal).find((m) => m.props("testid") === testid)!

const mountPage = async () => {
  vi.mocked(fetchTargetDescriptors).mockResolvedValue([{system: "BREVO", kind: "LIST"}])
  vi.mocked(fetchTargetOptions).mockResolvedValue([{
    system: "BREVO", externalId: "7", kind: "LIST", label: "Guests", folderLabel: "Newsletter",
    memberCount: 3, linkedTargetId: null, path: ["Brevo", "Newsletter"],
  }])
  vi.mocked(fetchTargetFolders).mockResolvedValue(["Newsletter"])
  const wrapper = mountInApp(CohortTargets)
  await flushPromises()
  return wrapper
}

describe("CohortTargets", () => {
  it("heads the page with the system's name and offers every target a move", async () => {
    const wrapper = await mountPage()

    expect(wrapper.findAllComponents(ManagerCard)[0]!.props("title")).toBe("Brevo")
    expect(wrapper.find("[data-testid=cohort-targets-selection-bar]").exists()).toBe(true)
    expect(wrapper.find("[data-testid=cohort-target-select-7]").exists()).toBe(true)
    expect(wrapper.find("[data-testid=cohort-target-move-7]").exists()).toBe(true)
  })

  it("titles a move of the selection by what Brevo holds", async () => {
    const wrapper = await mountPage()

    await wrapper.get("[data-testid=cohort-target-select-7] input").setValue(true)
    await wrapper.get("[data-testid=cohort-targets-move-selected]").trigger("click")
    await flushPromises()

    expect(modal(wrapper, "cohort-target-move-dialog").props("title")).toBe("Move 1 brevo list")
  })

  it("makes a list in a new folder from the new-list dialog", async () => {
    const wrapper = await mountPage()
    vi.mocked(createFolderInSystem).mockResolvedValue({ok: true, saved: ["Newsletter", "Projects"]})
    vi.mocked(createListInSystem).mockResolvedValue({ok: true, saved: {
      system: "BREVO", externalId: "9", kind: "LIST", label: "Pub quiz", folderLabel: "Projects",
      memberCount: 0, linkedTargetId: null, path: ["Brevo", "Projects"],
    }})

    await wrapper.get("[data-testid=cohort-targets-create]").trigger("click")
    await flushPromises()
    await wrapper.get("[data-testid=cohort-target-create-name] input").setValue("Pub quiz")
    const dialog = wrapper.findAllComponents({name: "VSelect"}).find((c) => c.attributes("data-testid") === "cohort-target-create-folder")!
    dialog.vm.$emit("update:modelValue", "__new__")
    await flushPromises()
    await wrapper.get("[data-testid=cohort-target-create-folder-name] input").setValue("Projects")
    modal(wrapper, "cohort-target-create-dialog").vm.$emit("save")
    await flushPromises()

    expect(createFolderInSystem).toHaveBeenCalledWith("BREVO", "Projects")
    expect(createListInSystem).toHaveBeenCalledWith("BREVO", "Pub quiz", "Projects")
    expect(wrapper.find("[data-testid=cohort-target-9]").exists()).toBe(true)
  })

  it("shows the system's reason when a rename is refused, and keeps the dialog open", async () => {
    const wrapper = await mountPage()
    vi.mocked(renameTarget).mockResolvedValue({ok: false, reason: "Brevo refused it: Bad Request"})

    await wrapper.get("[data-testid=cohort-target-rename-7]").trigger("click")
    await flushPromises()
    await wrapper.get("[data-testid=cohort-target-rename-name] input").setValue("Guests 2026")
    modal(wrapper, "cohort-target-rename-dialog").vm.$emit("save")
    await flushPromises()

    expect(renameTarget).toHaveBeenCalledWith("BREVO", "7", "Guests 2026")
    expect(modal(wrapper, "cohort-target-rename-dialog").props("modelValue")).toBe(true)
    expect(wrapper.text()).toContain("Brevo refused it: Bad Request")
  })

  it("keeps a refused new list open with the reason, and closes the dialogs on cancel or dismiss", async () => {
    const wrapper = await mountPage()
    vi.mocked(createListInSystem).mockResolvedValue({ok: false, reason: "Brevo refused it: Bad Request"})

    await wrapper.get("[data-testid=cohort-targets-create]").trigger("click")
    await flushPromises()
    await wrapper.get("[data-testid=cohort-target-create-name] input").setValue("Pub quiz")
    modal(wrapper, "cohort-target-create-dialog").vm.$emit("save")
    await flushPromises()
    expect(wrapper.get("[data-testid=cohort-target-write-refusal]").text()).toBe("Brevo refused it: Bad Request")

    modal(wrapper, "cohort-target-create-dialog").vm.$emit("cancel")
    await flushPromises()
    expect(modal(wrapper, "cohort-target-create-dialog").props("modelValue")).toBe(false)
    await wrapper.get("[data-testid=cohort-targets-create]").trigger("click")
    modal(wrapper, "cohort-target-create-dialog").vm.$emit("update:modelValue", false)
    await flushPromises()
    expect(modal(wrapper, "cohort-target-create-dialog").props("modelValue")).toBe(false)

    await wrapper.get("[data-testid=cohort-target-rename-7]").trigger("click")
    await flushPromises()
    modal(wrapper, "cohort-target-rename-dialog").vm.$emit("cancel")
    await flushPromises()
    expect(modal(wrapper, "cohort-target-rename-dialog").props("modelValue")).toBe(false)
    await wrapper.get("[data-testid=cohort-target-rename-7]").trigger("click")
    await flushPromises()
    modal(wrapper, "cohort-target-rename-dialog").vm.$emit("update:modelValue", false)
    await flushPromises()
    expect(modal(wrapper, "cohort-target-rename-dialog").props("modelValue")).toBe(false)
  })

  it("deletes an unlinked list only once its name is typed exactly", async () => {
    const wrapper = await mountPage()
    vi.mocked(deleteTarget).mockResolvedValue({ok: true})

    await wrapper.get("[data-testid=cohort-target-delete-7]").trigger("click")
    await flushPromises()
    await wrapper.get("[data-testid=cohort-target-delete-name] input").setValue("guests")
    expect(modal(wrapper, "cohort-target-delete-dialog").props("saveDisabled")).toBe(true)
    await wrapper.get("[data-testid=cohort-target-delete-name] input").setValue("Guests")
    expect(modal(wrapper, "cohort-target-delete-dialog").props("saveDisabled")).toBe(false)
    modal(wrapper, "cohort-target-delete-dialog").vm.$emit("save")
    await flushPromises()

    expect(deleteTarget).toHaveBeenCalledWith("BREVO", "7", "Guests")
    expect(wrapper.find("[data-testid=cohort-target-7]").exists()).toBe(false)
  })

  it("keeps a refused delete open with the reason, and closes it on cancel", async () => {
    const wrapper = await mountPage()
    vi.mocked(deleteTarget).mockResolvedValue({ok: false, reason: "A list linked to a cohort is archived, not deleted."})

    await wrapper.get("[data-testid=cohort-target-delete-7]").trigger("click")
    await flushPromises()
    await wrapper.get("[data-testid=cohort-target-delete-name] input").setValue("Guests")
    modal(wrapper, "cohort-target-delete-dialog").vm.$emit("save")
    await flushPromises()

    expect(wrapper.get("[data-testid=cohort-target-write-refusal]").text()).toBe("A list linked to a cohort is archived, not deleted.")
    modal(wrapper, "cohort-target-delete-dialog").vm.$emit("cancel")
    await flushPromises()
    expect(modal(wrapper, "cohort-target-delete-dialog").props("modelValue")).toBe(false)

    await wrapper.get("[data-testid=cohort-target-delete-7]").trigger("click")
    await flushPromises()
    modal(wrapper, "cohort-target-delete-dialog").vm.$emit("update:modelValue", false)
    await flushPromises()
    expect(modal(wrapper, "cohort-target-delete-dialog").props("modelValue")).toBe(false)
  })

  it("archives a list from its row, and says so when Brevo refuses", async () => {
    const wrapper = await mountPage()
    vi.mocked(archiveTarget).mockResolvedValue({ok: false, reason: "Brevo refused it: down"})

    await wrapper.get("[data-testid=cohort-target-archive-7]").trigger("click")
    await flushPromises()

    expect(archiveTarget).toHaveBeenCalledWith("BREVO", "7")
    expect(wrapper.get("[data-testid=cohort-targets-write-refusal]").text()).toContain("Brevo refused it: down")
  })

  it("shows the tidy's proposal and the folders it would make", async () => {
    const wrapper = await mountPage()
    vi.mocked(fetchTidyPlan).mockResolvedValue({
      moves: [{externalId: "7", label: "Guests", from: "Newsletter", to: "Members"}],
      foldersToCreate: ["Members"],
    })

    await wrapper.get("[data-testid=cohort-targets-tidy]").trigger("click")
    await flushPromises()

    expect(wrapper.get("[data-testid=cohort-targets-tidy-move-7]").text()).toContain("Newsletter → Members")
    expect(wrapper.get("[data-testid=cohort-targets-tidy-folders]").text()).toContain("Members")
    expect(modal(wrapper, "cohort-targets-tidy-dialog").props("saveLabel")).toBe("Move 1")
  })

  it("says so when every linked list is already in its folder", async () => {
    const wrapper = await mountPage()
    vi.mocked(fetchTidyPlan).mockResolvedValue({moves: [], foldersToCreate: []})

    await wrapper.get("[data-testid=cohort-targets-tidy]").trigger("click")
    await flushPromises()

    expect(wrapper.find("[data-testid=cohort-targets-tidy-nothing]").exists()).toBe(true)
  })

  it("moves the picked lists, keeps the dialog open over a failure, and closes it when all moved", async () => {
    const wrapper = await mountPage()
    const move = {externalId: "7", label: "Guests", from: "Newsletter", to: "Members"}
    vi.mocked(fetchTidyPlan).mockResolvedValue({moves: [move], foldersToCreate: []})
    vi.mocked(applyTidy)
      .mockResolvedValueOnce({moved: [], failed: [{externalId: "7", label: "Guests", message: "Brevo said no"}]})
      .mockResolvedValueOnce({moved: [], failed: []})

    await wrapper.get("[data-testid=cohort-targets-tidy]").trigger("click")
    await flushPromises()
    await wrapper.get("[data-testid=cohort-targets-tidy-pick-7] input").setValue(false)
    await wrapper.get("[data-testid=cohort-targets-tidy-pick-7] input").setValue(true)
    modal(wrapper, "cohort-targets-tidy-dialog").vm.$emit("save")
    await flushPromises()

    expect(applyTidy).toHaveBeenCalledWith("BREVO", ["7"])
    expect(wrapper.get("[data-testid=cohort-targets-tidy-failures]").text()).toBe("Guests: Brevo said no")
    expect(modal(wrapper, "cohort-targets-tidy-dialog").props("modelValue")).toBe(true)

    modal(wrapper, "cohort-targets-tidy-dialog").vm.$emit("save")
    await flushPromises()
    expect(modal(wrapper, "cohort-targets-tidy-dialog").props("modelValue")).toBe(false)
  })

  it("closes the tidy on cancel and when dismissed", async () => {
    const wrapper = await mountPage()
    vi.mocked(fetchTidyPlan).mockResolvedValue({moves: [], foldersToCreate: []})

    await wrapper.get("[data-testid=cohort-targets-tidy]").trigger("click")
    await flushPromises()
    modal(wrapper, "cohort-targets-tidy-dialog").vm.$emit("cancel")
    await flushPromises()
    expect(modal(wrapper, "cohort-targets-tidy-dialog").props("modelValue")).toBe(false)

    await wrapper.get("[data-testid=cohort-targets-tidy]").trigger("click")
    await flushPromises()
    modal(wrapper, "cohort-targets-tidy-dialog").vm.$emit("update:modelValue", false)
    await flushPromises()
    expect(modal(wrapper, "cohort-targets-tidy-dialog").props("modelValue")).toBe(false)
  })
})
