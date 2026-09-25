import {describe, expect, it, vi} from "vitest"
import {h} from "vue"
import AccountFrame from "@/components/common/AccountFrame.vue"
import {mountInApp} from "../../helpers/testUtils"

const {mockStore, mockRoute} = vi.hoisted(() => ({
  mockStore: {getters: {isLoggedIn: true, isBoard: false, isAdmin: false, getLogin: {userId: 3, addressId: 9 as number | null}}},
  mockRoute: {path: "/account/security"},
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {route: mockRoute})
})

const frame = (props: Record<string, unknown> = {}) =>
  mountInApp(AccountFrame, {props: {heading: "Security", ...props}})

const withContent = (props: Record<string, unknown>) =>
  mountInApp({render: () => h(AccountFrame, {heading: "Games", ...props}, () => h("p", {"data-testid": "content"}, "handles"))})

describe("the account frame", () => {
  it("heads every account page with its name and the account tabs, the page's own marked", () => {
    const wrapper = frame()

    expect(wrapper.find("h1").text()).toBe("Security")
    expect(wrapper.text()).toContain("Your account")
    expect(wrapper.findAll("[data-testid^=account-tab-]").map(tab => tab.text())).toEqual(["Account", "Security", "Esports Teams", "Address"])
    expect(wrapper.find("[aria-current=page]").text()).toBe("Security")
    expect(wrapper.find("[data-testid=account-tab-address]").attributes("to")).toBe("/account/addresses/9")
  })

  it("leaves Address out for somebody without one, as the account menu does", () => {
    mockStore.getters.getLogin.addressId = null
    const tabs = frame().findAll("[data-testid^=account-tab-]").map(tab => tab.text())
    mockStore.getters.getLogin.addressId = 9

    expect(tabs).toEqual(["Account", "Security", "Esports Teams"])
  })

  it("draws a crumb back instead of nothing, and no tabs where the page asks for none", () => {
    const wrapper = frame({crumb: {label: "Security", to: "/account/security"}, tabs: false})

    expect(wrapper.find("[data-testid=account-crumb]").attributes("to")).toBe("/account/security")
    expect(wrapper.find("[data-testid=account-crumb]").text()).toBe("Security")
    expect(wrapper.findAll("[data-testid^=account-tab-]")).toHaveLength(0)
  })

  it("keeps a page on Vuetify out of the island, and one on island parts inside it", () => {
    const vuetify = withContent({})
    expect(vuetify.find("[data-testid=account-island] [data-testid=content]").exists()).toBe(false)
    expect(vuetify.find("[data-testid=content]").exists()).toBe(true)

    const island = withContent({islandContent: true})
    expect(island.find("[data-testid=account-island] [data-testid=content]").exists()).toBe(true)
  })
})
