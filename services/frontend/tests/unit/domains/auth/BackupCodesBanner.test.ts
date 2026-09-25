import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import BackupCodesBanner from "@/domains/auth/components/BackupCodesBanner.vue"

const {mockStore, mockRoute} = vi.hoisted(() => ({
  mockStore: {getters: {getLogin: null as unknown}},
  mockRoute: {path: "/"},
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})
vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {route: mockRoute})
})

const VSnackbar = {name: "VSnackbar", props: ["modelValue"], template: "<div v-if='modelValue'><slot /><slot name='actions' /></div>"}
const VBtn = {name: "VBtn", props: ["to"], emits: ["click"], template: "<button @click=\"$emit('click')\"><slot /></button>"}

const signedIn = (on: boolean, backupCodesLeft: number) => {
  mockStore.getters.getLogin = {twoFactor: {on, backupCodesLeft, required: false, offered: false}}
}
const banner = () => mount(BackupCodesBanner, {global: {stubs: {VSnackbar, VBtn}}})

describe("the low backup codes banner", () => {
  beforeEach(() => {
    mockRoute.path = "/"
    mockStore.getters.getLogin = null
  })

  it("says so once three or fewer backup codes are left, and points at the two-factor page", async () => {
    signedIn(true, 1)
    const wrapper = banner()

    expect(wrapper.text()).toContain("1 backup code left.")
    expect(wrapper.findComponent(VBtn).props("to")).toBe("/account/security/two-factor")
    await wrapper.find("[data-testid=backup-codes-banner-open-btn]").trigger("click")
    expect(wrapper.find("[data-testid=backup-codes-banner-open-btn]").exists()).toBe(false)
  })

  it("goes away when put off", async () => {
    signedIn(true, 3)
    const wrapper = banner()

    expect(wrapper.text()).toContain("3 backup codes left.")
    await wrapper.find("[data-testid=backup-codes-banner-dismiss-btn]").trigger("click")
    expect(wrapper.text()).toBe("")
  })

  it("says nothing with codes to spare, without two-factor, signed out or on the two-factor page itself", () => {
    signedIn(true, 4)
    expect(banner().text()).toBe("")
    signedIn(false, 0)
    expect(banner().text()).toBe("")
    mockStore.getters.getLogin = null
    expect(banner().text()).toBe("")
    signedIn(true, 0)
    mockRoute.path = "/account/security/two-factor"
    expect(banner().text()).toBe("")
  })
})
