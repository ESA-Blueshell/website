import {describe, expect, it, vi} from "vitest"
import CohortSubjectDetail from "@/pages/management/CohortSubjectDetail.vue"
import {
  CohortKind,
  CohortSubjectCategory,
  CohortSubjectType,
  JobTrigger,
  TargetSystem,
  fetchCohortSubject,
  type CohortSubject,
} from "@/domains/cohorts/adapters/cohorts"
import type {StoredLogin} from "@/plugins/store"
import {mountPage} from "../../helpers/mountPage"
import {settle} from "../../helpers/testUtils"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  fetchCohortSubject: vi.fn(),
}))

const adminLogin: StoredLogin = {
  userId: 1,
  username: "admin",
  roles: ["ADMIN"] as StoredLogin["roles"],
  twoFactor: {backupCodesLeft: 0, mayTurnOff: false, offered: false, on: true, required: false},
}

const run = (startedAt: string, missing: number) => ({startedAt, trigger: JobTrigger.SCHEDULED_RUN, inStep: 40, missing, extra: 2})

const subject = (): CohortSubject => ({
  id: 7,
  label: "Paid 2026",
  description: null,
  category: CohortSubjectCategory.PERIODS,
  type: CohortSubjectType.PERIOD_PAYERS,
  definitionKey: "PERIOD_PAYERS:1",
  orphaned: false,
  mappings: [{
    cohortId: 40,
    system: TargetSystem.BREVO,
    kind: CohortKind.LIST,
    label: "Paid 2026",
    externalId: "7",
    lastReconciledAt: null,
    path: ["Brevo"],
    folderKnown: true,
    runs: [run("2026-09-29T03:00:00Z", 1), run("2026-09-28T03:00:00Z", 3)],
  }],
  members: [],
})

describe("CohortSubjectDetail drift", () => {
  it("shows each target's drift and the runs before it", async () => {
    vi.mocked(fetchCohortSubject).mockResolvedValue(subject())
    const wrapper = await mountPage(CohortSubjectDetail, {path: "/management/cohorts/subjects/7", login: adminLogin})
    await wrapper.get("[data-testid=cohort-subject-targets] [data-testid=info-box-toggle]").trigger("click")
    await settle()

    expect(wrapper.get("[data-testid=cohort-subject-target-drift-brevo]").text()).toBe("40 in step · 1 missing · 2 extra")
    expect(wrapper.get("[data-testid=cohort-subject-target-drift-history-brevo]").text()).toContain("nightly: 3 missing, 2 extra")
  })
})
