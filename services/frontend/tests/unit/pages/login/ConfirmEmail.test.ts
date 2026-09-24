import {beforeEach, describe, expect, it, vi} from "vitest"
import ConfirmEmail from "@/pages/login/ConfirmEmail.vue"
import {mountInApp, settle} from "../helpers"

const {mockConfirm, mockToken} = vi.hoisted(() => ({mockConfirm: vi.fn(), mockToken: vi.fn()}))

vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {route: {query: {}, hash: ""}, router: {replace: vi.fn()}})
})
vi.mock("@/domains/auth", () => ({confirmNewEmail: mockConfirm}))
vi.mock("@/plugins/recoveryToken", () => ({loadRecoveryTokenFromRoute: mockToken}))
vi.mock("@/components/common/banners/TopBanner.vue", () => ({default: {name: "TopBanner", template: "<div />"}}))

describe("confirming a new email address", () => {
  beforeEach(() => vi.clearAllMocks())

  it("says the account uses the new address once the link is confirmed", async () => {
    mockToken.mockReturnValue("s.v")
    mockConfirm.mockResolvedValue({ok: true, value: undefined})
    const wrapper = mountInApp(ConfirmEmail)
    await settle()

    expect(mockConfirm).toHaveBeenCalledWith("s.v")
    expect(wrapper.find("[data-testid=confirm-email-done]").exists()).toBe(true)
  })

  it("says why a link does not work", async () => {
    mockToken.mockReturnValue("s.v")
    mockConfirm.mockResolvedValue({ok: false, reason: "That address belongs to another account."})
    const refused = mountInApp(ConfirmEmail)
    await settle()
    expect(refused.find("[data-testid=confirm-email-refused]").text()).toBe("That address belongs to another account.")

    mockToken.mockReturnValue("")
    const empty = mountInApp(ConfirmEmail)
    await settle()
    expect(empty.find("[data-testid=confirm-email-refused]").text()).toBe("That link does not work any more.")
  })
})
