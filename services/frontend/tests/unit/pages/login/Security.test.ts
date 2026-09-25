import {beforeEach, describe, expect, it, vi} from "vitest"
import Security from "@/pages/login/Security.vue"
import {mountInApp, settle} from "../helpers"

const {mockStore, mockAuth} = vi.hoisted(() => ({
  mockStore: {commit: vi.fn(), getters: {getLogin: {userId: 3}}},
  mockAuth: {
    readTwoFactor: vi.fn(),
    listSignIns: vi.fn(),
    readMySecurityLog: vi.fn(),
    readEmailAddress: vi.fn(),
  },
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("@/domains/auth", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  ...mockAuth,
}))

vi.mock("@/components/common/AccountFrame.vue", () => ({
  default: {name: "AccountFrame", props: ["heading", "crumb", "islandContent", "tabs", "eyebrow", "body"], template: "<div><slot /></div>"},
}))

const standing = (on: boolean, backupCodesLeft = on ? 5 : 0, required = false) =>
  ({on, backupCodesLeft, required, offered: false, mayTurnOff: on, since: on ? "2026-09-12T10:00:00Z" : null})
const when = "2026-09-24T12:00:00Z"
const here = {id: "here", browser: "Firefox", platform: "Linux", signedInAt: "2026-09-22T09:00:00Z", lastSeenAt: when, current: true}
const there = {...here, id: "there", browser: "Safari", platform: "iOS", current: false}
const entry = (id: number, kind: string, occurredAt = when) =>
  ({id, kind, actorKind: "PERSON", occurredAt, browser: "Firefox", platform: "Linux"})

const open = async () => {
  const wrapper = mountInApp(Security)
  await settle()
  return wrapper
}
type Page = Awaited<ReturnType<typeof open>>
const row = (wrapper: Page, testid: string) => wrapper.get(`[data-testid=${testid}]`)

describe("the security hub", () => {
  beforeEach(() => {
    mockAuth.readTwoFactor.mockResolvedValue(standing(true))
    mockAuth.listSignIns.mockResolvedValue([here, there])
    mockAuth.readEmailAddress.mockResolvedValue({email: "alice@example.com", pendingEmail: null})
    mockAuth.readMySecurityLog.mockResolvedValue({
      events: [entry(3, "SIGNED_IN"), entry(2, "PASSWORD_CHANGED", "2026-09-12T19:58:00Z"), entry(1, "TWO_FACTOR_ON")],
      page: 0,
      totalPages: 1,
      totalElements: 3,
    })
  })

  it("stands where the account stands: two-factor, where it is signed in and the last change", async () => {
    const wrapper = await open()

    expect(wrapper.get("[data-testid=security-standing-two-factor]").text()).toContain("On")
    expect(wrapper.get("[data-testid=security-standing-two-factor]").text()).toContain("5 backup codes left")
    expect(wrapper.get("[data-testid=security-standing-sign-ins]").text()).toContain("2 browsers")
    expect(wrapper.get("[data-testid=security-standing-sign-ins]").text()).toContain("This one since Tue 22 Sep")
    expect(wrapper.get("[data-testid=security-standing-last-change]").text()).toContain("Password changed")
    expect(wrapper.get("[data-testid=security-standing-last-change]").text()).toContain("Sat 12 Sep · Firefox on Linux")
    expect(mockStore.commit).toHaveBeenCalledWith("setTwoFactor", standing(true))
  })

  it("opens a page for each thing somebody comes to do, saying what they can do there", async () => {
    const wrapper = await open()

    expect(row(wrapper, "security-two-factor").attributes("to")).toBe("/account/security/two-factor")
    expect(row(wrapper, "security-two-factor").text()).toContain("Make new backup codes, replace your authenticator app or turn it off")
    expect(row(wrapper, "security-password").text()).toContain("Change your password")
    expect(row(wrapper, "security-password").attributes("to")).toBe("/account/security/password")
    expect(row(wrapper, "security-email").attributes("to")).toBe("/account/security/email")
    expect(row(wrapper, "security-email").text()).toContain("Change the email address your account uses")
    expect(row(wrapper, "security-sign-ins").attributes("to")).toBe("/account/security/sign-ins")
    expect(row(wrapper, "security-sign-ins").text()).toContain("See where you are signed in and sign out anywhere")
    expect(row(wrapper, "security-log").attributes("to")).toBe("/account/security/log")
    expect(row(wrapper, "security-log").text()).toContain("Read every sign-in and change to your account's security")
  })

  it("shows a move to another address that is still waiting", async () => {
    mockAuth.readEmailAddress.mockResolvedValue({email: "alice@example.com", pendingEmail: "alice@utwente.nl"})
    const wrapper = await open()

    expect(row(wrapper, "security-email").text()).toContain("Waiting")
  })

  it("sends somebody without two-factor to the set-up that asks for their password", async () => {
    mockAuth.readTwoFactor.mockResolvedValue(standing(false))
    mockAuth.listSignIns.mockResolvedValue([here])
    const wrapper = await open()

    expect(row(wrapper, "security-two-factor").attributes("to")).toBe("/account/security/two-factor/set-up")
    expect(row(wrapper, "security-two-factor").text()).toContain("Off")
    expect(wrapper.get("[data-testid=security-standing-sign-ins]").text()).toContain("1 browser")
    expect(row(wrapper, "security-two-factor").text()).toContain("Set up a code from your phone on top of your password")
  })

  it("leaves a granted role waiting on two-factor to the router, which sends it to its own set-up", async () => {
    mockAuth.readTwoFactor.mockResolvedValue(standing(false, 0, true))
    const wrapper = await open()

    expect(row(wrapper, "security-two-factor").attributes("to")).toBe("/account/security/two-factor/set-up")
    expect(wrapper.get("[data-testid=security-standing-two-factor]").text()).toContain("Your role waits for it")
  })

  it("offers a granted role no way to turn two-factor off", async () => {
    mockAuth.readTwoFactor.mockResolvedValue({...standing(true), mayTurnOff: false})
    const wrapper = await open()

    expect(row(wrapper, "security-two-factor").text()).toContain("Make new backup codes or replace your authenticator app")
  })

  it("asks for new backup codes before they run out", async () => {
    mockAuth.readTwoFactor.mockResolvedValue(standing(true, 1))
    const wrapper = await open()

    expect(wrapper.get("[data-testid=security-standing-two-factor]").text()).toContain("1 backup code left")
    expect(wrapper.get("[data-testid=security-backup-codes-low]").text()).toContain("1 backup code left")
    expect(row(wrapper, "security-backup-codes-low-btn").attributes("to")).toBe("/account/security/two-factor")
  })

  it("says so when nothing has changed lately", async () => {
    mockAuth.readMySecurityLog.mockResolvedValue({events: [], page: 0, totalPages: 0, totalElements: 0})
    const wrapper = await open()

    expect(wrapper.get("[data-testid=security-standing-last-change]").text()).toContain("Nothing yet")
  })
})
