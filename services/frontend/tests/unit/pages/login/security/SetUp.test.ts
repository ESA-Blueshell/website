import {beforeEach, describe, expect, it, vi} from "vitest"
import SetUp from "@/pages/login/security/SetUp.vue"
import {mountInApp, settle} from "../../helpers"

const {mockStore, mockAuth, mockRoute, mockReplace} = vi.hoisted(() => ({
  mockStore: {commit: vi.fn(), getters: {getLogin: {userId: 3}}},
  mockAuth: {readTwoFactor: vi.fn()},
  mockRoute: {query: {} as Record<string, string>},
  mockReplace: vi.fn(),
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../../helpers/testUtils")
  return withVueRouter(importOriginal, {route: mockRoute, router: {replace: mockReplace}})
})

vi.mock("@/domains/auth", async (importOriginal) => {
  const {stepUpStub} = await import("./stubs")
  return {
    ...(await importOriginal<Record<string, unknown>>()),
    ...mockAuth,
    StepUpDialog: stepUpStub,
    TwoFactorSetUp: {name: "TwoFactorSetUp", props: ["mode", "leave"], emits: ["done", "stepUp"], template: "<div />"},
  }
})

vi.mock("@/components/common/AccountFrame.vue", async () => ({default: (await import("./stubs")).frameStub}))

const standing = (on: boolean) => ({on, backupCodesLeft: on ? 10 : 0, required: false, offered: false, mayTurnOff: on})

const open = async () => {
  const wrapper = mountInApp(SetUp)
  await settle()
  return wrapper
}

describe("the regular set-up page", () => {
  beforeEach(() => {
    mockRoute.query = {}
    mockAuth.readTwoFactor.mockResolvedValue(standing(false))
  })

  it("sets up with the password first, and goes on to the two-factor page", async () => {
    const wrapper = await open()
    const setUp = wrapper.getComponent({name: "TwoFactorSetUp"})
    expect(setUp.props("mode")).toBe("voluntary")
    expect(setUp.props("leave")).toBe("/account/security")
    expect(wrapper.getComponent({name: "AccountFrame"}).props("heading")).toBe("Set up two-factor")

    mockAuth.readTwoFactor.mockResolvedValue(standing(true))
    setUp.vm.$emit("done")
    await settle()

    expect(mockStore.commit).toHaveBeenCalledWith("setTwoFactor", standing(true))
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Two-factor authentication is on.")
    expect(mockReplace).toHaveBeenCalledWith("/account/security/two-factor")
  })

  it("replaces an app behind a step-up, and goes on to where the person was going", async () => {
    mockRoute.query = {replace: "1", redirect: "/events"}
    mockAuth.readTwoFactor.mockResolvedValue(standing(true))
    const wrapper = await open()
    const setUp = wrapper.getComponent({name: "TwoFactorSetUp"})
    expect(setUp.props("mode")).toBe("replace")
    expect(wrapper.getComponent({name: "AccountFrame"}).props("heading")).toBe("Replace your app")

    const retry = vi.fn()
    setUp.vm.$emit("stepUp", retry)
    await settle()
    const dialog = wrapper.getComponent({name: "StepUpDialog"})
    expect(dialog.props("modelValue")).toBe(true)
    expect(dialog.props("twoFactorOn")).toBe(true)
    dialog.vm.$emit("proved")
    expect(retry).toHaveBeenCalled()
    dialog.vm.$emit("update:modelValue", false)
    await settle()
    expect(dialog.props("modelValue")).toBe(false)

    setUp.vm.$emit("done")
    await settle()
    expect(mockReplace).toHaveBeenCalledWith("/events")
  })
})
