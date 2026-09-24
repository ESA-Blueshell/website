import {beforeEach, describe, expect, it, vi} from "vitest"

const {mockReadJsonCookie, mockWriteJsonCookie, mockDeleteCookie, mockEmitAuthChanged} = vi.hoisted(() => ({
  mockReadJsonCookie: vi.fn(),
  mockWriteJsonCookie: vi.fn(),
  mockDeleteCookie: vi.fn(),
  mockEmitAuthChanged: vi.fn(),
}))

vi.mock("@/plugins/cookies", () => ({
  readJsonCookie: mockReadJsonCookie,
  writeJsonCookie: mockWriteJsonCookie,
  deleteCookie: mockDeleteCookie,
}))

vi.mock("@/plugins/authSync", () => ({
  emitAuthChanged: mockEmitAuthChanged,
}))

vi.mock("@/services/api", () => ({
  Role: {
    ADMIN: "ADMIN",
    BOARD: "BOARD",
    COMMITTEE: "COMMITTEE",
    MEMBER: "MEMBER",
  },
}))

import store from "@/plugins/store"

describe("store plugin", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    store.commit("setLoginState", null)
  })

  it("stores login in state and cookie", () => {
    store.commit("setLogin", {
      token: "jwt-token",
      username: "emma",
      roles: ["MEMBER"],
      expiration: Date.now() + 100_000,
    } as never)

    expect(store.getters.isLoggedIn).toBe(true)
    expect(mockWriteJsonCookie).toHaveBeenCalledWith("login", expect.objectContaining({username: "emma"}))
    expect(mockWriteJsonCookie).not.toHaveBeenCalledWith("login", expect.objectContaining({token: expect.anything()}))
    expect(mockEmitAuthChanged).toHaveBeenCalled()
  })

  it("writes neither the token nor the reported expiry to the cookie", () => {
    store.commit("setLogin", {
      token: "jwt-token",
      username: "emma",
      userId: 7,
      roles: ["MEMBER"],
      expiration: Date.now() + 100_000,
    } as never)

    const written = mockWriteJsonCookie.mock.calls.at(-1)![1] as Record<string, unknown>
    expect(Object.keys(written)).not.toContain("expiration")
    expect(Object.keys(written)).not.toContain("token")
    expect(written).toMatchObject({username: "emma", userId: 7, roles: ["MEMBER"]})
    expect(store.getters.getLogin).not.toHaveProperty("expiration")
  })

  it("logs out and clears login cookie", () => {
    store.commit("setLoginState", {
      username: "emma",
      roles: ["MEMBER"],
      expiration: Date.now() + 100_000,
    } as never)

    store.commit("logout")
    expect(store.getters.isLoggedIn).toBe(false)
    expect(mockDeleteCookie).toHaveBeenCalledWith("login")
    expect(mockEmitAuthChanged).toHaveBeenCalled()
  })

  /**
   * The expiry the sign-in response reported is wrong by the end of the first day — the api
   * re-issues a token while a sign-in is in use — so nothing may read it to decide whether the
   * reader is signed in. This is the number that used to send them back to the login page.
   */
  it("keeps a reader signed in once the reported expiry has passed", () => {
    store.commit("setLoginState", {
      username: "emma",
      roles: ["MEMBER"],
      expiration: Date.now() - 100_000,
    } as never)

    expect(store.getters.isLoggedIn).toBe(true)
  })

  it("computes role-specific getters", () => {
    store.commit("setLoginState", {
      username: "board-admin",
      roles: ["BOARD", "ADMIN", "MEMBER"],
      expiration: Date.now() + 100_000,
    } as never)

    expect(store.getters.isBoard).toBe(true)
    expect(store.getters.isAdmin).toBe(true)
    expect(store.getters.isMember).toBe(true)
  })

  it("keeps the api's word on two-factor, and nothing without a sign-in", () => {
    const standing = {on: true, backupCodesLeft: 10, required: false, offered: false}
    store.commit("setLoginState", null)
    store.commit("setTwoFactor", standing)
    expect(store.getters.getLogin).toBeNull()

    store.commit("setLoginState", {username: "emma", roles: ["MEMBER"]} as never)
    store.commit("setTwoFactor", standing)
    expect(store.getters.getLogin.twoFactor).toEqual(standing)
    expect(mockWriteJsonCookie).toHaveBeenLastCalledWith("login", expect.objectContaining({twoFactor: standing}))
  })

  it("sets and reads xsrf token", () => {
    store.commit("setXsrfToken", "token-123")
    expect(store.getters.getXsrfToken).toBe("token-123")
  })
})
