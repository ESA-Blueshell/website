import {describe, expect, it} from "vitest"
import ManagementShell from "@/components/management/ManagementShell.vue"
import ManagementBar from "@/components/management/ManagementBar.vue"
import ManagementMore from "@/pages/management/ManagementMore.vue"
import {isOn, managementFor} from "@/components/management/managementNav"
import router from "@/plugins/router"
import type {StoredLogin} from "@/plugins/store"
import {boardLogin, mountPage} from "../../helpers/mountPage"
import {settle} from "../../helpers/testUtils"

const adminLogin: StoredLogin = {...boardLogin, roles: ["ADMIN", "BOARD"] as StoredLogin["roles"]}
const memberLogin: StoredLogin = {...boardLogin, roles: ["MEMBER"] as StoredLogin["roles"]}

describe("Management's navigation", () => {
  it("lists what the reader may open, and leaves admin pages to the admin", () => {
    const pages = (reader: {board: boolean, admin: boolean}) =>
      managementFor(reader).map((group) => [group.label, ...group.entries.map((entry) => entry.label)])

    expect(pages({board: true, admin: false})).toEqual([["Members", "Users", "Account recovery"], ["Mail", "Sent"]])
    expect(pages({board: true, admin: true})).toEqual([
      ["Members", "Users", "Account recovery"],
      ["Mail", "Sent"],
      ["Platforms", "Cohorts"],
      ["System", "Jobs"],
    ])
    expect(pages({board: false, admin: false})).toEqual([])
  })

  it("marks the page the reader is on, and a page below it", () => {
    const users = {label: "Users", to: "/management/users"}

    expect(isOn("/management/users", users)).toBe(true)
    expect(isOn("/management/users/4", users)).toBe(true)
    expect(isOn("/management/users-old", users)).toBe(false)
    expect(isOn("/management", users)).toBe(false)
  })
})

// Mounting through the app's router loads each page lazily, which is slow under a full run.
describe("the Management portal", {timeout: 20_000}, () => {
  // RouterLink is stubbed in the unit suite, so each link carries its target as `to`.
  const targets = (wrapper: Awaited<ReturnType<typeof mountPage>>, within: string) =>
    wrapper.findAll(`[data-testid=${within}] a`).map((link) => link.attributes("to"))

  it("groups its pages in a sidebar and marks the admin's own", async () => {
    const admin = await mountPage(ManagementShell, {path: "/management/more", login: adminLogin})

    expect(targets(admin, "management-sidebar")).toEqual(["/user-manager", "/recovery/manage", "/management/emails", "/management/cohorts", "/management/jobs"])
    expect(admin.get("[data-testid=management-sidebar]").text()).toContain("@Admin")

    // The store is the app's one, so the board's sign-in replaces the admin's.
    const board = await mountPage(ManagementShell, {path: "/management/more", login: boardLogin})

    expect(targets(board, "management-sidebar")).toEqual(["/user-manager", "/recovery/manage", "/management/emails"])
    expect(board.get("[data-testid=management-sidebar]").text()).not.toContain("@Admin")
  })

  it("gives a phone a bottom bar with the members tab and More, lit for the page open", async () => {
    const wrapper = await mountPage(ManagementShell, {path: "/management/more", login: boardLogin, width: 390})

    expect(targets(wrapper, "management-tabbar")).toEqual(["/user-manager", "/management/more"])
    expect(wrapper.get("[data-testid=management-tab-more]").classes()).toContain("mg-tab--on")
    expect(wrapper.get("[data-testid=management-tab-members]").classes()).not.toContain("mg-tab--on")
  })

  it("lists every page on More, grouped like the sidebar", async () => {
    const wrapper = await mountPage(ManagementMore, {path: "/management/more", login: boardLogin})

    expect(wrapper.get("[data-testid=management-more]").text()).toContain("Members")
    expect(wrapper.find("[data-testid=management-more-account-recovery]").exists()).toBe(true)
    expect(wrapper.find("[data-testid=management-more-jobs]").exists()).toBe(false)
  })

  it("offers the theme, the account pages, the way back to the site and logging out", async () => {
    const wrapper = await mountPage(ManagementBar, {path: "/management/more", login: boardLogin})
    const items = () => wrapper.findAllComponents({name: "DropdownMenuItem"})

    await wrapper.get("[data-testid=management-theme]").trigger("click")
    expect(wrapper.emitted("toggleDarkMode")).toHaveLength(1)
    await wrapper.get("[data-testid=management-account]").trigger("click")
    await settle()
    expect(items().map((item) => item.find("a").exists() ? item.find("a").attributes("to") : item.text()))
      .toEqual(["/account", "/account/security", "/account/games", "/account/addresses", "/", "Log out"])
    expect(items().at(-2)!.text()).toBe("Back to the site")
    items().at(-1)!.vm.$emit("select", new Event("select"))
    expect(wrapper.emitted("logOut")).toHaveLength(1)
  })
})

describe("the ways into Management", {timeout: 20_000}, () => {
  it("opens on the list of its pages", async () => {
    await mountPage(ManagementShell, {path: "/", login: boardLogin})

    await router.push("/management")

    expect(router.currentRoute.value.path).toBe("/management/more")
    expect(router.currentRoute.value.meta.management).toBe(true)
  })

  it("turns away anyone without a role that may use it", async () => {
    await mountPage(ManagementShell, {path: "/", login: memberLogin})

    await router.push("/management/more")

    expect(router.currentRoute.value.path).toBe("/")
  })

  it("asks a visitor to log in first, and remembers where they were going", async () => {
    await mountPage(ManagementShell, {path: "/", login: null})

    await router.push("/management/more")

    expect(router.currentRoute.value.path).toBe("/login")
    expect(router.currentRoute.value.query.redirect).toBe("/management/more")
  })
})
