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
      username: "emma",
      roles: ["MEMBER"],
      expiration: Date.now() + 100_000,
    } as never)

    expect(store.getters.isLoggedIn).toBe(true)
    expect(mockWriteJsonCookie).toHaveBeenCalledWith("login", expect.objectContaining({username: "emma"}))
    expect(mockEmitAuthChanged).toHaveBeenCalled()
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

  it("sets and reads xsrf token", () => {
    store.commit("setXsrfToken", "token-123")
    expect(store.getters.getXsrfToken).toBe("token-123")
  })
})
