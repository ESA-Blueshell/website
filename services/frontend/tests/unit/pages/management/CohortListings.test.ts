import {beforeEach, describe, expect, it, vi} from "vitest"
import CohortDashboard from "@/pages/management/CohortDashboard.vue"
import CohortCategory from "@/pages/management/CohortCategory.vue"
import {CohortCategory as Category, CohortType, fetchCohorts, type CohortSummary} from "@/domains/cohorts/adapters/cohorts"
import router from "@/plugins/router"
import type {StoredLogin} from "@/plugins/store"
import {mountPage} from "../../helpers/mountPage"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  fetchCohorts: vi.fn(),
}))

const adminLogin: StoredLogin = {
  userId: 1,
  username: "admin",
  roles: ["ADMIN"] as StoredLogin["roles"],
  twoFactor: {backupCodesLeft: 0, mayTurnOff: false, offered: false, on: true, required: false},
}

const cohorts: CohortSummary[] = [
  {id: 1, label: "Sitecie", category: Category.COMMITTEES, type: CohortType.COMMITTEE_MEMBERS, memberCount: 4, mappingCount: 1},
  {id: 2, label: "Paid 2026", category: Category.PERIODS, type: CohortType.PERIOD_PAYERS, memberCount: 90, mappingCount: 1},
]

describe("the cohort listings", () => {
  beforeEach(() => {
    vi.mocked(fetchCohorts).mockResolvedValue(cohorts)
  })

  it("counts every cohort and opens a category", async () => {
    const wrapper = await mountPage(CohortDashboard, {path: "/management/cohorts", login: adminLogin})

    expect(wrapper.get("[data-testid=cohort-total-count]").text()).toContain("2")
    expect(wrapper.get("[data-testid=cohort-category-card-committees]").text()).toContain("4")
    await wrapper.get("[data-testid=cohort-category-card-committees]").trigger("click")

    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe("cohortCategory"))
  })

  it("lists one category's cohorts and opens one by click or key", async () => {
    const wrapper = await mountPage(CohortCategory, {path: "/management/cohorts/committees", login: adminLogin})
    // Mounted outside a router view, the page would follow its own navigation; the push is what matters.
    const push = vi.spyOn(router, "push").mockResolvedValue(undefined)

    expect(wrapper.find("[data-testid=cohort-row-1]").exists()).toBe(true)
    expect(wrapper.find("[data-testid=cohort-row-2]").exists()).toBe(false)
    for (const open of ["click", "keydown.enter", "keydown.space"] as const) {
      await wrapper.get("[data-testid=cohort-row-1]").trigger(open)
    }

    expect(push).toHaveBeenCalledTimes(3)
    expect(push).toHaveBeenCalledWith({name: "cohortDetail", params: {id: 1}})
    push.mockRestore()
  })
})
