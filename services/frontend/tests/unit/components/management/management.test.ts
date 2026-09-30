import {beforeEach, describe, expect, it, vi} from "vitest"
import ManagementShell from "@/components/management/ManagementShell.vue"
import ManagementBar from "@/components/management/ManagementBar.vue"
import ManagementMore from "@/pages/management/ManagementMore.vue"
import {isOn, managementFor} from "@/components/management/managementNav"
import router from "@/plugins/router"
import type {StoredLogin} from "@/plugins/store"
import {boardLogin, mountPage} from "../../helpers/mountPage"
import {settle} from "../../helpers/testUtils"

const {mockListAlerts} = vi.hoisted(() => ({mockListAlerts: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  listAlerts: mockListAlerts,
}))

const anAlert = (key: string, hidden = false) => ({key, kind: "JOB_DEAD", count: 1, hidden})

beforeEach(() => {
  mockListAlerts.mockResolvedValue({status: 200, data: []})
})

const adminLogin: StoredLogin = {...boardLogin, roles: ["ADMIN", "BOARD"] as StoredLogin["roles"]}
const memberLogin: StoredLogin = {...boardLogin, roles: ["MEMBER"] as StoredLogin["roles"]}

describe("Management's navigation", () => {
  it("lists what the reader may open, and leaves admin pages to the admin", () => {
    const board = managementFor({board: true, admin: false}).flatMap((group) => group.entries.map((entry) => entry.to))
    const admin = managementFor({board: true, admin: true}).flatMap((group) => group.entries.map((entry) => entry.to))

    expect(board).toContain("/management/users")
    expect(board).not.toContain("/management/jobs")
    expect(admin).toContain("/management/jobs")
    expect(managementFor({board: false, admin: false})).toEqual([])
  })

  it("marks the page the reader is on, and a page below it", () => {
    const users = {label: "Users", to: "/management/users"}

    expect(isOn("/management/users", users)).toBe(true)
    expect(isOn("/management/users/4", users)).toBe(true)
    expect(isOn("/management/users-old", users)).toBe(false)
  })
})

// Mounting through the app's router loads each page lazily, which is slow under a full run.
describe("the Management portal", {timeout: 20_000}, () => {
  it("groups its pages in a sidebar, marks the one open and the admin-only ones", async () => {
    const wrapper = await mountPage(ManagementShell, {path: "/management/jobs", login: adminLogin})

    const current = wrapper.get("[data-testid=management-nav-jobs]")
    expect(current.attributes("aria-current")).toBe("page")
    expect(wrapper.get("[data-testid=management-sidebar]").text()).toContain("@Admin")
    expect(wrapper.find("[data-testid=management-nav-users]").attributes("aria-current")).toBeUndefined()
  })

  it("gives a phone a bottom bar with the alerts and members tabs and More", async () => {
    const wrapper = await mountPage(ManagementShell, {path: "/management/users", login: boardLogin, width: 390})

    // RouterLink is stubbed in the unit suite, so each link carries its target as `to`.
    const tabs = wrapper.findAll("[data-testid=management-tabbar] a").map((tab) => tab.attributes("to"))
    expect(tabs).toEqual(["/management/alerts", "/management/users", "/management/more"])
    expect(wrapper.get("[data-testid=management-tab-members]").classes()).toContain("mg-tab--on")
  })

  it("counts the reader's alerts, hidden ones left out, in the sidebar, the phone bar and the account menu", async () => {
    mockListAlerts.mockResolvedValue({status: 200, data: [anAlert("a"), anAlert("b"), anAlert("c", true)]})
    const shell = await mountPage(ManagementShell, {path: "/management/users", login: boardLogin, width: 390})
    await settle()

    expect(shell.get("[data-testid=management-nav-alerts-count]").text()).toContain("2")
    expect(shell.get("[data-testid=management-tab-alerts-count]").text()).toBe("2")

    const bar = await mountPage(ManagementBar, {path: "/management/users", login: boardLogin})
    await bar.get("[data-testid=management-account]").trigger("click")
    await settle()
    const alerts = bar.findAllComponents({name: "DropdownMenuItem"}).map((item) => item.find("[data-testid=management-account-alerts]"))
      .find((link) => link.exists())
    expect(alerts?.attributes("to")).toBe("/management/alerts")
    expect(alerts?.text()).toContain("2")
  })

  it("reads the alerts again on every page", async () => {
    await mountPage(ManagementShell, {path: "/management/users", login: boardLogin})
    const before = mockListAlerts.mock.calls.length

    await router.push("/management/recovery")
    await settle()

    expect(mockListAlerts.mock.calls.length).toBeGreaterThan(before)
  })

  it("lists every page on More, grouped like the sidebar", async () => {
    const wrapper = await mountPage(ManagementMore, {path: "/management/more", login: boardLogin})

    expect(wrapper.find("[data-testid=management-more-account-recovery]").exists()).toBe(true)
    expect(wrapper.find("[data-testid=management-more-jobs]").exists()).toBe(false)
  })

  it("offers the account pages, the way back to the site and signing out", async () => {
    const wrapper = await mountPage(ManagementBar, {path: "/management/users", login: boardLogin})

    await wrapper.get("[data-testid=management-theme]").trigger("click")
    expect(wrapper.emitted("toggleDarkMode")).toHaveLength(1)
    await wrapper.get("[data-testid=management-account]").trigger("click")
    await settle()
    const back = wrapper.findAllComponents({name: "DropdownMenuItem"}).map((item) => item.find("[data-testid=management-back-to-site]"))
      .find((link) => link.exists())
    expect(back?.attributes("to")).toBe("/")
    const out = wrapper.findAllComponents({name: "DropdownMenuItem"}).find((item) => item.attributes("data-testid") === "management-log-out")
    out?.vm.$emit("select", new Event("select"))
    expect(wrapper.emitted("logOut")).toHaveLength(1)
  })
})

describe("the ways into Management", {timeout: 20_000}, () => {
  it("sends each old management address to its new page", async () => {
    await mountPage(ManagementShell, {path: "/", login: adminLogin})
    const cases: [string, string][] = [
      ["/user-manager", "/management/users"],
      ["/addresses/manage", "/management/addresses"],
      ["/recovery/manage", "/management/recovery"],
      ["/management/emails", "/management/mail/sent"],
      ["/management/cohorts", "/management/platforms/brevo"],
      ["/management/cohorts/targets", "/management/platforms/brevo/lists"],
      ["/management/cohort/7", "/management/platforms/brevo/cohort/7"],
      ["/management/cohorts/periods", "/management/platforms/brevo/periods"],
      ["/management", "/management/users"],
    ]

    for (const [from, to] of cases) {
      await router.push(from)
      expect(router.currentRoute.value.path, from).toBe(to)
    }
  })

  it("shows the unauthorized page to anyone without a role that may use it", async () => {
    await mountPage(ManagementShell, {path: "/", login: memberLogin})

    await router.push("/management/users")
    expect(router.currentRoute.value.path).toBe("/unauthorized")

    await mountPage(ManagementShell, {path: "/", login: boardLogin})
    await router.push("/management/jobs")
    expect(router.currentRoute.value.path).toBe("/unauthorized")
  })
})
