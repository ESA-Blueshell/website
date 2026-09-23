import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import CohortPicker from "@/components/form/fields/CohortPicker.vue"

const {mockFetchCohortOptions, mockHandleNetworkError} = vi.hoisted(() => ({
  mockFetchCohortOptions: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/domains/cohorts", () => ({fetchCohortOptions: mockFetchCohortOptions}))
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))

const cohort = {id: 1, label: "Members", system: "BREVO", kind: "LIST", memberCount: 12}
const stubs = {FormField: {template: "<div><slot /></div>"}}
const picker = (wrapper: ReturnType<typeof mount>) => wrapper.findComponent({name: "SearchPicker"})

describe("CohortPicker", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetchCohortOptions.mockResolvedValue([cohort])
  })

  it("names a cohort by its label, where it lives and how many it holds", async () => {
    const wrapper = mount(CohortPicker, {global: {stubs}})
    await flushPromises()

    expect(picker(wrapper).props("options")).toEqual([
      {key: "1", label: "Members", note: "BREVO LIST (12)", terms: ["BREVO", "LIST"]},
    ])
  })

  it("stops saying it is looking once the list is in", async () => {
    const wrapper = mount(CohortPicker, {global: {stubs}})
    await flushPromises()

    expect(picker(wrapper).props("loading")).toBe(false)
  })

  it("reports a list it could not read", async () => {
    mockFetchCohortOptions.mockRejectedValue(new Error("500"))
    const wrapper = mount(CohortPicker, {global: {stubs}})
    await flushPromises()

    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect(picker(wrapper).props("loading")).toBe(false)
    expect(picker(wrapper).props("options")).toEqual([])
  })
})
