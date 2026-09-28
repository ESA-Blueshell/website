import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import TargetPickerModal from "@/domains/cohorts/components/TargetPickerModal.vue"
import {fetchTargetOptions} from "@/domains/cohorts/adapters/cohorts"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  fetchTargetOptions: vi.fn(),
}))

const mountPicker = async (mode: "add" | "switch") => {
  vi.mocked(fetchTargetOptions).mockResolvedValue([])
  const wrapper = mount(TargetPickerModal, {
    props: {modelValue: false, mode, subjectId: 1, system: "BREVO", cohortId: 2},
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
})
