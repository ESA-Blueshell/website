import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import ManagementDashboard from "@/pages/management/ManagementDashboard.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  listAlerts: vi.fn(),
  findCurrentPeriodStanding: vi.fn(),
  findEvents: vi.fn(),
  getStats1: vi.fn(),
  findCohorts: vi.fn(),
  getStats: vi.fn(),
  listExceptions: vi.fn(),
}))
const {mockStore} = vi.hoisted(() => ({mockStore: {commit: vi.fn(), getters: {isAdmin: false}}}))

vi.mock("@/plugins/store", () => ({default: mockStore}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const event = (id: number, title: string) => ({id, title, startTime: "2026-10-20T19:00:00", endTime: "2026-10-20T23:00:00", approved: true})

describe("the Management dashboard", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(ManagementDashboard)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isAdmin = false
    api.listAlerts.mockResolvedValue({
      status: 200,
      data: [
        {key: "target-drift:2", kind: "TARGET_DRIFT", count: 3, hidden: false, subjectId: 2, subjectLabel: "Sitecie"},
        {key: "cohort-without-list:5", kind: "COHORT_WITHOUT_LIST", count: 1, hidden: false, subjectId: 5, subjectLabel: "Paid"},
      ],
    })
    api.findCurrentPeriodStanding.mockResolvedValue({
      status: 200,
      data: {periodId: 3, startDate: "2026-09-01", endDate: "2027-08-31", members: 211, paid: 180, stillToPay: 31, pendingFirstContribution: 9},
    })
    api.findEvents.mockImplementation(({query}: {query: {approved: boolean}}) => Promise.resolve({
      status: 200,
      data: query.approved
        ? {content: [event(8, "LAN party")], page: {totalElements: 1}}
        : {content: [event(9, "Pub quiz")], page: {totalElements: 4}},
    }))
    api.getStats1.mockResolvedValue({status: 200, data: {totalCount: 50, deliveredCount: 40, openedCount: 20, failedCount: 2, bouncedCount: 1, pendingCount: 0, sentCount: 7}})
    api.findCohorts.mockResolvedValue({status: 200, data: [{id: 1}, {id: 2}]})
    api.getStats.mockResolvedValue({status: 200, data: {queuedCount: 1, runningCount: 2, failedCount: 3, deadCount: 4}})
    api.listExceptions.mockResolvedValue({status: 200, data: [{id: 1}, {id: 2}]})
  })

  afterEach(() => {
    unmountAll(wrappers, "ManagementDashboard")
  })

  it("sums up each area and links it to its page, reading no admin figure for the board", async () => {
    const wrapper = await mount()

    const alerts = wrapper.get('[data-testid="dashboard-alerts"]')
    expect(alerts.text()).toContain("A list is out of step")
    expect(alerts.text()).toContain("Sitecie · 3 people differ")
    expect(alerts.get('[data-testid="dashboard-alerts-link"]').text()).toBe("All 2 alerts")
    const membership = wrapper.get('[data-testid="dashboard-membership"]').text()
    expect(membership).toContain("Members, 2026-2027")
    expect(membership).toContain("211")
    expect(membership).toContain("9 pending their first contribution")
    expect(membership).toContain("31 still to pay")
    const events = wrapper.get('[data-testid="dashboard-events"]')
    expect(events.get('[data-testid="dashboard-events-queue"]').text()).toBe("4 awaiting approval")
    expect(events.text()).toContain("Pub quiz")
    expect(events.text()).toContain("LAN party")
    expect(events.text()).toContain("20 Oct 19:00")
    expect(wrapper.get('[data-testid="dashboard-mail"]').text()).toContain("40 delivered · 3 failed or bounced")
    const platforms = wrapper.get('[data-testid="dashboard-platforms"]')
    expect(platforms.get('[data-testid="dashboard-platforms-link"]').attributes("to")).toBe("/management/platforms/brevo")
    expect(platforms.text()).toContain("2 cohorts · 1 out of step · 1 without a list")

    expect(wrapper.find('[data-testid="dashboard-system"]').exists()).toBe(false)
    expect(api.getStats).not.toHaveBeenCalled()
    expect(api.listExceptions).not.toHaveBeenCalled()
  })

  it("adds the system figures, marked Admin, for an admin", async () => {
    mockStore.getters.isAdmin = true
    const wrapper = await mount()

    const system = wrapper.get('[data-testid="dashboard-system"]')
    expect(system.text()).toContain("@Admin")
    expect(system.text()).toContain("4 dead · 3 failed")
    expect(wrapper.get('[data-testid="dashboard-platforms"]').text()).toContain("2 open")
    expect(api.listExceptions).toHaveBeenCalledWith({query: {resolved: false}})
  })

  it("says so when nothing needs the reader and no period exists yet", async () => {
    api.listAlerts.mockResolvedValue({status: 200, data: []})
    api.findCurrentPeriodStanding.mockResolvedValue({status: 204, data: undefined})
    api.getStats1.mockResolvedValue({status: 500, error: {}})
    api.findEvents.mockResolvedValue({status: 200, data: {content: []}})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="dashboard-alerts"]').text()).toContain("Nothing needs you right now.")
    expect(wrapper.get('[data-testid="dashboard-membership"]').text()).toContain("There is no contribution period yet.")
    expect(wrapper.get('[data-testid="dashboard-events"]').text()).toContain("0 awaiting approval")
    expect(wrapper.find('[data-testid="dashboard-mail"]').exists()).toBe(false)
  })

  it("names a period that starts and ends in one year by that year, and greets by the hour", async () => {
    api.findCurrentPeriodStanding.mockResolvedValue({
      status: 200,
      data: {periodId: 3, startDate: "2026-01-01", endDate: "2026-12-31", members: 6, paid: 1, stillToPay: 5, pendingFirstContribution: 0},
    })
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="dashboard-membership"]').text()).toContain("Members, 2026")
    expect(wrapper.get('[data-testid="dashboard-membership"]').text()).not.toContain("2026-2026")
    expect(wrapper.text()).toMatch(/Good (morning|afternoon|evening)\. Here is what needs you/)
  })
})
