import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import FieldGallery from "@/pages/design/FieldGallery.vue"

const {mockCohorts, mockEvents, mockPeriods, mockUsers, mockSearch} = vi.hoisted(() => ({
  mockCohorts: vi.fn(), mockEvents: vi.fn(), mockPeriods: vi.fn(), mockUsers: vi.fn(),
  mockSearch: vi.fn(),
}))

vi.mock("@/domains/cohorts", () => ({fetchCohortOptions: mockCohorts}))
vi.mock("@/domains/user", () => ({searchMemberAccounts: mockSearch}))
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
    expect(wrapper.find(".gallery").classes()).toContain("island-dark")

    await wrapper.find(".gallery__theme").trigger("click")

    expect(wrapper.find(".gallery").classes()).not.toContain("island-dark")
  })
})
