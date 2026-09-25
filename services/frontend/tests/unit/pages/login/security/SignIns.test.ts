import {beforeEach, describe, expect, it, vi} from "vitest"
import SignIns from "@/pages/login/security/SignIns.vue"
import {mountInApp, settle} from "../../helpers"
import {ok, refused} from "./stubs"

const {mockStore, mockAuth, mockReplace} = vi.hoisted(() => ({
  mockStore: {commit: vi.fn(), getters: {getLogin: {userId: 3}}},
  mockReplace: vi.fn(),
  mockAuth: {
    listSignIns: vi.fn(),
    listTrustedBrowsers: vi.fn(),
    endOneSignIn: vi.fn(),
    endOtherSignIns: vi.fn(),
    endEverySignIn: vi.fn(),
    forgetOneTrustedBrowser: vi.fn(),
    forgetEveryTrustedBrowser: vi.fn(),
  },
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../../helpers/testUtils")
  return withVueRouter(importOriginal, {router: {replace: mockReplace}})
})

vi.mock("@/domains/auth", async (importOriginal) => ({...(await importOriginal<Record<string, unknown>>()), ...mockAuth}))

vi.mock("@/components/common/AccountFrame.vue", async () => ({default: (await import("./stubs")).frameStub}))

const when = "2026-09-24T12:00:00Z"
const here = {id: "here", browser: "Firefox", platform: "Linux", signedInAt: "2026-09-22T09:00:00Z", lastSeenAt: when, current: true}
const phone = {...here, id: "phone", browser: "Safari", platform: "iOS", current: false}
const trusted = {id: 4, browser: "Firefox", platform: "Linux", trustedAt: "2026-09-22T09:00:00Z", expiresAt: "2026-10-22T09:00:00Z", lastUsedAt: null}

const open = async () => {
  const wrapper = mountInApp(SignIns)
  await settle()
  return wrapper
}
type Page = Awaited<ReturnType<typeof open>>
const press = async (wrapper: Page, selector: string) => {
  await wrapper.get(selector).trigger("click")
  await settle()
}

describe("the sign-ins page", () => {
  beforeEach(() => {
    mockAuth.listSignIns.mockResolvedValue([here, phone])
    mockAuth.listTrustedBrowsers.mockResolvedValue([trusted])
    for (const write of Object.values(mockAuth).slice(2)) write.mockResolvedValue(ok())
  })

  it("lists every sign-in with this one marked, and counts them on their headings", async () => {
    const wrapper = await open()

    const rows = wrapper.findAll("[data-testid=security-sign-in]")
    expect(rows.map(row => row.find(".cut-row__title").text().replace(/\s+/gu, " "))).toEqual(["Firefox on Linux This browser", "Safari on iOS"])
    expect(rows[0]!.text()).toContain("Signed in Tue 22 Sep")
    expect(rows[0]!.find("button").exists()).toBe(false)
    expect(wrapper.get("[data-testid=security-sign-ins-count]").text()).toContain("2")
    expect(wrapper.get("[data-testid=security-trusted-browsers-count]").text()).toContain("1")
    expect(wrapper.get("[data-testid=security-trusted-browser]").text()).toContain("Trusted Tue 22 Sep · until Thu 22 Oct")
  })

  it("ends one sign-in, or every other one", async () => {
    const wrapper = await open()

    await press(wrapper, "[data-testid=security-sign-in] + [data-testid=security-sign-in] button")
    expect(mockAuth.endOneSignIn).toHaveBeenCalledWith("phone")

    mockAuth.endOtherSignIns.mockResolvedValue(refused("They could not be ended."))
    await press(wrapper, "[data-testid=security-sign-out-elsewhere-btn]")
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "They could not be ended.")
    expect(mockAuth.listSignIns).toHaveBeenCalledTimes(3)
  })

  it("offers signing out everywhere else only where there is somewhere else", async () => {
    mockAuth.listSignIns.mockResolvedValue([here])
    expect((await open()).find("[data-testid=security-sign-out-elsewhere-btn]").exists()).toBe(false)
  })

  it("signs out everywhere, this browser too, and goes to the login page", async () => {
    mockAuth.endEverySignIn.mockResolvedValueOnce(refused("Signing out everywhere failed."))
    const wrapper = await open()

    await press(wrapper, "[data-testid=security-sign-out-everywhere-btn]")
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Signing out everywhere failed.")
    expect(mockReplace).not.toHaveBeenCalled()

    await press(wrapper, "[data-testid=security-sign-out-everywhere-btn]")
    expect(mockStore.commit).toHaveBeenCalledWith("logout")
    expect(mockReplace).toHaveBeenCalledWith("/login")
  })

  it("forgets one trusted browser or all of them", async () => {
    mockAuth.forgetOneTrustedBrowser.mockResolvedValueOnce(refused("That browser could not be forgotten."))
    const wrapper = await open()

    await press(wrapper, "[data-testid=security-trusted-browser] button")
    expect(mockAuth.forgetOneTrustedBrowser).toHaveBeenCalledWith(4)
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "That browser could not be forgotten.")

    await press(wrapper, "[data-testid=security-forget-all-btn]")
    expect(mockAuth.forgetEveryTrustedBrowser).toHaveBeenCalled()
    expect(mockAuth.listTrustedBrowsers).toHaveBeenCalledTimes(3)
  })

  it("says so when no browser is trusted", async () => {
    mockAuth.listTrustedBrowsers.mockResolvedValue([])
    const wrapper = await open()

    expect(wrapper.get("[data-testid=security-trusted-browsers]").text()).toContain("No browser skips the code at sign-in.")
    expect(wrapper.find("[data-testid=security-forget-all-btn]").exists()).toBe(false)
  })
})
