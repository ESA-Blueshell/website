import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import ContributionPeriodPage from "@/pages/management/ContributionPeriodPage.vue"
import {aContributionPeriod} from "../../helpers/apiFixtures"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({findContributionPeriods: vi.fn(), deleteContributionPeriodById: vi.fn()}))
const {mockRoute, mockPush, mockHandleNetworkError} = vi.hoisted(() => ({
  mockRoute: {params: {} as Record<string, string>},
  mockPush: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => mockRoute,
  useRouter: () => ({push: mockPush}),
}))

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

describe("a contribution period's page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async (id?: string) => {
    mockRoute.params = id ? {id} : {}
    const wrapper = mountInApp(ContributionPeriodPage)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.findContributionPeriods.mockResolvedValue({status: 200, data: [aContributionPeriod({id: 2, startDate: "2025-09-01", endDate: "2026-08-31"})]})
    api.deleteContributionPeriodById.mockResolvedValue({status: 204, data: undefined})
  })

  afterEach(() => {
    unmountAll(wrappers, "ContributionPeriodPage")
  })

  it("opens a new period empty, and goes to the period once it is saved", async () => {
    const wrapper = await mount()

    expect(wrapper.text()).toContain("New contribution period")
    expect(wrapper.find('[data-testid="contribution-period-danger"]').exists()).toBe(false)
    wrapper.findComponent({name: "ContributionPeriodForm"}).vm.$emit("changed", aContributionPeriod({id: 7}))
    expect(mockPush).toHaveBeenCalledWith("/management/contributions/7")
    wrapper.findComponent({name: "ContributionPeriodForm"}).vm.$emit("cancelled")
    expect(mockPush).toHaveBeenLastCalledWith("/management/contributions")
  })

  it("deletes a period only once its name is typed", async () => {
    const wrapper = await mount("2")

    expect(wrapper.text()).toContain("Contribution period 2025–26")
    const remove = () => wrapper.get('[data-testid="contribution-period-delete-btn"]')
    expect(remove().attributes("disabled")).toBeDefined()
    await remove().trigger("click")
    expect(api.deleteContributionPeriodById).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="contribution-period-delete-name"]').setValue("2025–26")
    expect(remove().attributes("disabled")).toBeUndefined()
    await remove().trigger("click")
    await settle()
    expect(api.deleteContributionPeriodById).toHaveBeenCalledWith({path: {id: 2}, throwOnError: true})
    expect(mockPush).toHaveBeenCalledWith("/management/contributions")

    wrapper.findComponent({name: "ContributionPeriodForm"}).vm.$emit("cancelled")
    expect(mockPush).toHaveBeenLastCalledWith("/management/contributions/2")
  })

  it("says so when the period is not there, or cannot be read or deleted", async () => {
    expect((await mount("9")).find('[data-testid="contribution-period-missing"]').exists()).toBe(true)

    api.deleteContributionPeriodById.mockRejectedValue(new Error("in use"))
    const wrapper = await mount("2")
    await wrapper.get('[data-testid="contribution-period-delete-name"]').setValue("2025–26")
    await wrapper.get('[data-testid="contribution-period-delete-btn"]').trigger("click")
    await settle()
    expect(mockHandleNetworkError).toHaveBeenCalled()

    api.findContributionPeriods.mockRejectedValue(new Error("offline"))
    await mount("2")
    expect(mockHandleNetworkError).toHaveBeenCalledTimes(2)
  })
})
