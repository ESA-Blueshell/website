import {beforeEach, describe, expect, it, vi} from "vitest"
import CohortCategory from "@/pages/management/CohortCategory.vue"
import CohortDashboard from "@/pages/management/CohortDashboard.vue"
import CohortDetail from "@/pages/management/CohortDetail.vue"
import {mountInApp, settle} from "../helpers"

const {mockRouter} = vi.hoisted(() => ({mockRouter: {replace: vi.fn(), push: vi.fn()}}))

vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {route: {params: {category: "committee", id: "7"}, query: {}}, router: mockRouter})
})
vi.mock("@/components/common/banners/TopBanner.vue", () => ({
  default: {name: "TopBanner", props: ["title"], template: "<div data-testid='banner'>{{ title }}</div>"},
}))

describe("the old cohort pages", () => {
  beforeEach(() => vi.clearAllMocks())

  for (const [name, page] of [
    ["the dashboard", CohortDashboard],
    ["a category", CohortCategory],
    ["one cohort", CohortDetail],
  ] as const) {
    it(`${name} stands in one main area and sends anybody but an admin home`, async () => {
      const wrapper = mountInApp(page)
      await settle()

      expect(wrapper.findAll("main")).toHaveLength(1)
      expect(wrapper.get("main").find("[data-testid='banner']").exists()).toBe(true)
      expect(mockRouter.replace).toHaveBeenCalledWith("/")
    })
  }
})
