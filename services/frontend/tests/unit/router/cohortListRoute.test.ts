import {beforeEach, describe, expect, it, vi} from "vitest"
import router, {cohortListRoute} from "@/plugins/router"

const {fetchCohort} = vi.hoisted(() => ({fetchCohort: vi.fn()}))
vi.mock("@/domains/cohorts", async (importOriginal) => ({...(await importOriginal<object>()), fetchCohort}))

describe("a link naming a cohort", () => {
  beforeEach(() => vi.clearAllMocks())

  it("lands on the cohort's Brevo list, or on Brevo where it has none or cannot be read", async () => {
    const to = router.resolve("/management/platforms/brevo/cohort/3")
    fetchCohort.mockResolvedValueOnce({mappings: [{system: "GOOGLE_WORKSPACE", externalId: "g"}, {system: "BREVO", externalId: "7"}]})
    await expect(cohortListRoute(to)).resolves.toBe("/management/platforms/brevo/lists/7")
    expect(fetchCohort).toHaveBeenCalledWith(3)

    fetchCohort.mockResolvedValueOnce({mappings: [{system: "BREVO", externalId: null}]})
    await expect(cohortListRoute(to)).resolves.toBe("/management/platforms/brevo")
    fetchCohort.mockRejectedValueOnce(new Error("down"))
    await expect(cohortListRoute(to)).resolves.toBe("/management/platforms/brevo")
  })

  it("draws the list page where the guard lets it through", async () => {
    const record = router.getRoutes().find((one) => one.path === "/management/platforms/brevo/cohort/:id")
    await expect((record?.components?.default as () => Promise<unknown>)()).resolves.toBeDefined()
  }, 20_000)
})
