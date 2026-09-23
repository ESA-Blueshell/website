import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import FieldGallery from "@/pages/design/FieldGallery.vue"
// The app loads its rules in main.ts; the sign-up form builder's fields validate as they mount.
import "@/plugins/validation"

const {mockCohorts, mockEvents, mockPeriods, mockUsers, mockSearch} = vi.hoisted(() => ({
  mockCohorts: vi.fn(), mockEvents: vi.fn(), mockPeriods: vi.fn(), mockUsers: vi.fn(),
  mockSearch: vi.fn(),
}))

vi.mock("@/domains/cohorts", () => ({fetchCohortOptions: mockCohorts}))
vi.mock("@/domains/user", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  searchMemberAccounts: mockSearch,
}))
vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  findEvents: mockEvents,
  findContributionPeriods: mockPeriods,
  findUsers: mockUsers,
}))

describe("the page every island field is drawn on", () => {
  it("draws each field, and reads in both halves of the theme", async () => {
    mockCohorts.mockResolvedValue([])
    mockEvents.mockResolvedValue({data: {content: []}})
    mockPeriods.mockResolvedValue({data: {content: []}})
    mockUsers.mockResolvedValue({data: {content: []}})
    mockSearch.mockResolvedValue([])

    const wrapper = mount(FieldGallery, {attachTo: document.body})
    await flushPromises()

    expect(wrapper.findAllComponents({name: "FormField"}).length).toBeGreaterThan(15)
    expect(wrapper.findComponent({name: "DateInput"}).exists()).toBe(true)
    expect(wrapper.findComponent({name: "TimeInput"}).exists()).toBe(true)
    expect(wrapper.findComponent({name: "MoneyInput"}).exists()).toBe(true)
    expect(wrapper.findComponent({name: "MarkdownEditor"}).exists()).toBe(true)
    expect(wrapper.findAll(".question__type").map(one => one.text()))
      .toEqual(["Description", "Open question", "Multiple choice", "Checkboxes"])
    expect(wrapper.find(".gallery").classes()).toContain("island-dark")

    await wrapper.find(".gallery__theme").trigger("click")

    expect(wrapper.find(".gallery").classes()).not.toContain("island-dark")
  })

  it("keeps what is written into any of them", async () => {
    mockCohorts.mockResolvedValue([])
    mockEvents.mockResolvedValue({data: {content: []}})
    mockPeriods.mockResolvedValue({data: {content: []}})
    mockUsers.mockResolvedValue({data: {content: []}})
    mockSearch.mockResolvedValue([])

    const wrapper = mount(FieldGallery, {attachTo: document.body})
    await flushPromises()

    // Every field on the page is written into, so the page holds what each one reports rather
    // than drawing them and dropping it.
    const written: Record<string, unknown> = {
      FormControl: "7", CheckBox: true, RadioGroup: "never", FileInput: null,
      UserPicker: 1, UserSelect: 2, CohortPicker: 3, ContributionPeriodPicker: 4,
      EventPicker: 5, MemberTypeSelect: "ALUMNI", EnumPicker: "CONTRIBUTION_PAID",
      CountrySelect: "BE", NationalitySelect: "BE",
    }
    for (const [name, value] of Object.entries(written)) {
      for (const one of wrapper.findAllComponents({name})) one.vm.$emit("update:modelValue", value)
    }
    await flushPromises()

    expect(wrapper.findComponent({name: "MoneyInput"}).props("modelValue")).toBe("7")
    expect(wrapper.findComponent({name: "TimeInput"}).props("modelValue")).toBe("7")
    expect(wrapper.findComponent({name: "CheckBox"}).props("modelValue")).toBe(true)
  })
})
