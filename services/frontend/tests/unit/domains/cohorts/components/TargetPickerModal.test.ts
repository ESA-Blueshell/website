import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import TargetPickerModal from "@/domains/cohorts/components/TargetPickerModal.vue"
import {TargetKind, TargetSystem, fetchTargetOptions, linkExistingTargetForCohort, switchCohortTarget} from "@/domains/cohorts/adapters/cohorts"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  fetchTargetOptions: vi.fn(),
  linkExistingTargetForCohort: vi.fn(),
  switchCohortTarget: vi.fn(),
}))

const mountPicker = async (mode: "add" | "switch") => {
  vi.mocked(fetchTargetOptions).mockResolvedValue([])
  const wrapper = mount(TargetPickerModal, {
    props: {modelValue: false, mode, cohortId: 1, system: "BREVO", targetId: 2},
    global: {stubs: {VDialog: {name: "VDialog", template: "<div><slot /></div>"}}},
  })
  // Opening is what loads the catalogue.
  await wrapper.setProps({modelValue: true})
  await flushPromises()
  return wrapper
}

const labels = (wrapper: Awaited<ReturnType<typeof mountPicker>>) =>
  wrapper.findAll("label").map(label => label.text())

describe("TargetPickerModal", () => {
  it("names the system's lists when adding, and offers a new one in any folder", async () => {
    const wrapper = await mountPicker("add")

    expect(labels(wrapper)).toContain("Brevo list")
    expect(fetchTargetOptions).toHaveBeenCalledWith("BREVO")

    await wrapper.findAll("[data-testid=target-picker-tabs] button").find(tab => tab.text() === "Create new")!.trigger("click")
    await flushPromises()
    expect(labels(wrapper)).toEqual(expect.arrayContaining(["New brevo list name", "Folder (optional)"]))
  })

  it("picks another list by name when switching", async () => {
    const wrapper = await mountPicker("switch")

    expect(labels(wrapper)).toContain("Brevo list")
    expect(wrapper.find("[data-testid=target-picker-tabs]").exists()).toBe(false)
  })

  it("switches the target it was opened for", async () => {
    vi.mocked(switchCohortTarget).mockResolvedValue({
      targetId: 2, system: TargetSystem.BREVO, kind: TargetKind.LIST, label: "Paid", externalId: "7",
      lastReconciledAt: null, path: [], folderKnown: true, runs: [], enforced: false,
    })
    const wrapper = await mountPicker("switch")

    await wrapper.get("[data-testid=target-picker-submit]").trigger("click")
    await flushPromises()

    expect(switchCohortTarget).toHaveBeenCalledWith(1, 2, "", false, false)
    expect(wrapper.emitted("saved")).toHaveLength(1)
  })

  it("says the cohort already has a target on the system instead of linking a second", async () => {
    vi.mocked(linkExistingTargetForCohort).mockResolvedValue({type: "conflict"})
    const wrapper = await mountPicker("add")

    await wrapper.get("[data-testid=target-picker-submit]").trigger("click")
    await flushPromises()

    expect(wrapper.get("[data-testid=target-picker-conflict]").text()).toBe("This cohort already has a BREVO target. Switch the existing one instead.")
  })
})
