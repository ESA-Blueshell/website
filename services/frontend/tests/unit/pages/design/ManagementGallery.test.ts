import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import ManagementGallery from "@/pages/design/ManagementGallery.vue"
import FilterPicker from "@/components/island/FilterPicker.vue"
import {JobExecutionStatus} from "@/domains/jobs"

const firstRow = (wrapper: ReturnType<typeof mount>) => wrapper.get("[data-testid=gallery-list] .gallery__cell").text()

describe("the management parts gallery", () => {
  it("draws every part, in either half of the theme", async () => {
    const wrapper = mount(ManagementGallery)

    expect(wrapper.find("[data-testid=gallery-search]").exists()).toBe(true)
    expect(wrapper.findAll(".state-mark")).toHaveLength(6)
    await wrapper.get("[data-testid=management-gallery-theme]").trigger("click")
    expect(wrapper.get("[data-theme]").attributes("data-theme")).toBe("light")
  })

  it("takes its picker's values from the SDK and clears the filters", async () => {
    const wrapper = mount(ManagementGallery)
    const picker = wrapper.findComponent(FilterPicker)

    expect(picker.props("options").map((option: {key: string}) => option.key)).toEqual(Object.values(JobExecutionStatus))
    picker.vm.$emit("update:modelValue", JobExecutionStatus.FAILED)
    await wrapper.get("[data-testid=gallery-search]").setValue("brevo")
    await wrapper.get("[data-testid=gallery-filters-clear]").trigger("click")
    expect(picker.props("modelValue")).toBeNull()
  })

  it("sorts the long list by either column, newest first by time", async () => {
    const wrapper = mount(ManagementGallery)

    expect(firstRow(wrapper)).toBe("Member 1")
    await wrapper.get("[data-testid=gallery-sort-when]").trigger("click")
    expect(firstRow(wrapper)).toBe("Member 5000")
    await wrapper.get("[data-testid=gallery-sort-name]").trigger("click")
    expect(firstRow(wrapper)).toBe("Member 1")
    await wrapper.get("[data-testid=gallery-sort-name]").trigger("click")
    expect(firstRow(wrapper)).toBe("Member 5000")
  })

  it("clears the selection and folds the fold-out", async () => {
    const wrapper = mount(ManagementGallery)

    await wrapper.get("[data-testid=gallery-selection-clear]").trigger("click")
    expect(wrapper.find("[data-testid=gallery-selection]").exists()).toBe(false)
    await wrapper.get("[data-testid=gallery-fold-toggle]").trigger("click")
    expect(wrapper.text()).not.toContain("Work that opens in place")
  })
})
