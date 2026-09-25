import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import PageTabs from "@/components/island/PageTabs.vue"

const {mockRoute} = vi.hoisted(() => ({mockRoute: {path: "/account"}}))

vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {route: mockRoute})
})

const entries = [
  {label: "Account", to: "/account"},
  {label: "Security", to: "/account/security"},
  {label: "Games", to: "/account/games"},
]

const marked = (path: string) => {
  mockRoute.path = path
  const wrapper = mount(PageTabs, {props: {entries, label: "Your account", testid: "account-tab"}})
  return wrapper.findAll("[aria-current=page]").map(tab => tab.text())
}

describe("page tabs", () => {
  it("draws a tab per page, named for it", () => {
    const wrapper = mount(PageTabs, {props: {entries, label: "Your account", testid: "account-tab"}})

    expect(wrapper.find("nav").attributes("aria-label")).toBe("Your account")
    expect(wrapper.findAll("[data-testid^=account-tab-]").map(tab => [tab.text(), tab.attributes("to")])).toEqual([
      ["Account", "/account"],
      ["Security", "/account/security"],
      ["Games", "/account/games"],
    ])
  })

  it("marks the tab the page is under, the closest one where two cover it", () => {
    expect(marked("/account")).toEqual(["Account"])
    expect(marked("/account/security")).toEqual(["Security"])
    expect(marked("/account/security/password")).toEqual(["Security"])
    expect(marked("/account/lock")).toEqual(["Account"])
    expect(marked("/events")).toEqual([])
  })
})
