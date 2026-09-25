import {beforeEach, describe, expect, it, vi} from "vitest"
import SetUpRequired from "@/pages/login/SetUpRequired.vue"
import {mountInApp, settle} from "../helpers"

const {mockStore, mockAuth, mockUser, mockRoute, mockReplace} = vi.hoisted(() => ({
  mockStore: {commit: vi.fn(), getters: {getLogin: {userId: 3}}},
  mockAuth: {readTwoFactor: vi.fn(), signOut: vi.fn()},
  mockUser: {readUser: vi.fn()},
  mockRoute: {query: {} as Record<string, string>},
  mockReplace: vi.fn(),
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {route: mockRoute, router: {replace: mockReplace}})
})

vi.mock("@/domains/auth", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  ...mockAuth,
  TwoFactorSetUp: {name: "TwoFactorSetUp", props: ["mode"], emits: ["done"], template: "<div />"},
}))

vi.mock("@/domains/user", () => mockUser)

vi.mock("@/components/common/AccountFrame.vue", async () => ({default: (await import("./security/stubs")).frameStub}))

const standing = {on: true, backupCodesLeft: 10, required: false, offered: false, mayTurnOff: false}

describe("the set-up a granted role is kept on after signing in", () => {
  beforeEach(() => {
    mockRoute.query = {}
    mockAuth.readTwoFactor.mockResolvedValue(standing)
    mockUser.readUser.mockResolvedValue({id: 3, roles: ["MEMBER", "BOARD"]})
  })

  it("stands apart from the account pages, with signing out the only way off", async () => {
    const wrapper = mountInApp(SetUpRequired)

    expect(wrapper.getComponent({name: "AccountFrame"}).props("tabs")).toBe(false)
    expect(wrapper.getComponent({name: "AccountFrame"}).props("crumb")).toBeUndefined()
    expect(wrapper.getComponent({name: "TwoFactorSetUp"}).props("mode")).toBe("required")

    await wrapper.get("[data-testid=two-factor-sign-out-btn]").trigger("click")
    await settle()

    expect(mockAuth.signOut).toHaveBeenCalled()
    expect(mockStore.commit).toHaveBeenCalledWith("logout")
    expect(mockReplace).toHaveBeenCalledWith("/login")
  })

  it("turns the role on with two-factor and carries on to where they were going", async () => {
    mockRoute.query = {redirect: "/management/users"}
    const wrapper = mountInApp(SetUpRequired)

    wrapper.getComponent({name: "TwoFactorSetUp"}).vm.$emit("done")
    await settle()

    expect(mockStore.commit).toHaveBeenCalledWith("setTwoFactor", standing)
    expect(mockStore.commit).toHaveBeenCalledWith("setRoles", ["MEMBER", "BOARD"])
    expect(mockReplace).toHaveBeenCalledWith("/management/users")
  })

  it("goes home where nothing was asked for", async () => {
    mockAuth.readTwoFactor.mockResolvedValue(null)
    mockUser.readUser.mockResolvedValue(null)
    const wrapper = mountInApp(SetUpRequired)

    wrapper.getComponent({name: "TwoFactorSetUp"}).vm.$emit("done")
    await settle()

    expect(mockReplace).toHaveBeenCalledWith("/")
    expect(mockStore.commit).not.toHaveBeenCalledWith("setRoles", expect.anything())
  })
})
