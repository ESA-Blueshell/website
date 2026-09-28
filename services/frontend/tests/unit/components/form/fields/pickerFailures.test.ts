import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import CohortPicker from "@/components/form/fields/CohortPicker.vue"
import PeriodPicker from "@/components/form/fields/ContributionPeriodPicker.vue"
import EventPicker from "@/components/form/fields/EventPicker.vue"
import UserPicker from "@/components/form/fields/UserPicker.vue"

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
    mockEvents.mockImplementation(async () => {
      throw new Error("offline")
    })

    const wrapper = mount(EventPicker, {props: {}, global: {stubs}})
    await flushPromises()

    expect(mockNetworkError).toHaveBeenCalled()
    expect(wrapper.findComponent({name: "SearchPicker"}).props("options")).toEqual([])
  })

  it("says a period list that failed", async () => {
    mockPeriods.mockImplementation(async () => {
      throw new Error("offline")
    })

    const wrapper = mount(PeriodPicker, {props: {}, global: {stubs}})
    await flushPromises()

    expect(mockNetworkError).toHaveBeenCalled()
    expect(wrapper.findComponent({name: "SearchPicker"}).props("options")).toEqual([])
  })

  it("says a people list that failed, once the list is opened", async () => {
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
