import {describe, expect, it} from "vitest"
import ManagementShell from "@/components/management/ManagementShell.vue"
import ManagementBar from "@/components/management/ManagementBar.vue"
import ManagementMore from "@/pages/management/ManagementMore.vue"
import {firstPageFor, isOn, managementFor} from "@/components/management/managementNav"
import router from "@/plugins/router"
import type {StoredLogin} from "@/plugins/store"
import {boardLogin, mountPage} from "../../helpers/mountPage"
import {settle} from "../../helpers/testUtils"

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
    expect(firstPageFor({board: true, admin: false})).toBe("/management/users")
    expect(firstPageFor({board: false, admin: false})).toBeNull()
  })
})

describe("the Management portal", () => {
  it("groups its pages in a sidebar, marks the one open and the admin-only ones", async () => {
    const wrapper = await mountPage(ManagementShell, {path: "/management/jobs", login: adminLogin})

    const current = wrapper.get("[data-testid=management-nav-jobs]")
    expect(current.attributes("aria-current")).toBe("page")
    expect(wrapper.get("[data-testid=management-sidebar]").text()).toContain("@Admin")
    expect(wrapper.find("[data-testid=management-nav-users]").attributes("aria-current")).toBeUndefined()
  })

  it("gives a phone a bottom bar with the members tab and More", async () => {
    const wrapper = await mountPage(ManagementShell, {path: "/management/users", login: boardLogin, width: 390})

    // RouterLink is stubbed in the unit suite, so each link carries its target as `to`.
    const tabs = wrapper.findAll("[data-testid=management-tabbar] a").map((tab) => tab.attributes("to"))
    expect(tabs).toEqual(["/management/users", "/management/more"])
    expect(wrapper.get("[data-testid=management-tab-members]").classes()).toContain("mg-tab--on")
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

describe("the ways into Management", () => {
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
