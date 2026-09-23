import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"

const {mockCohorts, mockEvents, mockPeriods, mockUsers, mockNetworkError} = vi.hoisted(() => ({
  mockCohorts: vi.fn(),
  mockEvents: vi.fn(),
  mockPeriods: vi.fn(),
  mockUsers: vi.fn(),
  mockNetworkError: vi.fn(),
}))
vi.mock("@/domains/cohorts", () => ({fetchCohortOptions: mockCohorts}))
vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  findEvents: mockEvents,
  findContributionPeriods: mockPeriods,
  findUsers: mockUsers,
}))
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockNetworkError}))

const stubs = {FormField: {template: "<div><slot /></div>"}}

describe("a picker whose fetch fails", () => {
  it("says so and stops waiting, rather than sitting there loading", async () => {
    const {default: CohortPicker} = await import("@/components/form/fields/CohortPicker.vue")
    mockCohorts.mockImplementation(async () => {
      throw new Error("offline")
    })

    const wrapper = mount(CohortPicker, {props: {}, global: {stubs}})
    await flushPromises()

    expect(mockNetworkError).toHaveBeenCalledOnce()
    expect(wrapper.findComponent({name: "SearchPicker"}).props("loading")).toBe(false)
    expect(wrapper.findComponent({name: "SearchPicker"}).props("options")).toEqual([])
  })

  it("says an event list that failed", async () => {
    const {default: EventPicker} = await import("@/components/form/fields/EventPicker.vue")
    mockEvents.mockImplementation(async () => {
      throw new Error("offline")
    })

    const wrapper = mount(EventPicker, {props: {}, global: {stubs}})
    await flushPromises()

    expect(mockNetworkError).toHaveBeenCalled()
    expect(wrapper.findComponent({name: "SearchPicker"}).props("options")).toEqual([])
  })

  it("says a period list that failed", async () => {
    const {default: PeriodPicker} =
      await import("@/components/form/fields/ContributionPeriodPicker.vue")
    mockPeriods.mockImplementation(async () => {
      throw new Error("offline")
    })

    const wrapper = mount(PeriodPicker, {props: {}, global: {stubs}})
    await flushPromises()

    expect(mockNetworkError).toHaveBeenCalled()
    expect(wrapper.findComponent({name: "SearchPicker"}).props("options")).toEqual([])
  })

  it("says a people list that failed, once the list is opened", async () => {
    const {default: UserPicker} = await import("@/components/form/fields/UserPicker.vue")
    mockUsers.mockImplementation(async () => {
      throw new Error("offline")
    })

    const wrapper = mount(UserPicker, {props: {}, global: {stubs}})
    wrapper.findComponent({name: "SearchPicker"}).vm.$emit("opened")
    await flushPromises()

    expect(mockNetworkError).toHaveBeenCalled()
    expect(wrapper.findComponent({name: "SearchPicker"}).props("loading")).toBe(false)
  })
})
