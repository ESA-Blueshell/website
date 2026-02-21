import {beforeEach, describe, expect, it, vi} from "vitest"
import type {TypedStore} from "@/plugins/store"

const {mockReadJsonCookie, mockDeleteCookie} = vi.hoisted(() => ({
  mockReadJsonCookie: vi.fn(),
  mockDeleteCookie: vi.fn(),
}))

vi.mock("@/plugins/cookies", () => ({
  readJsonCookie: mockReadJsonCookie,
  deleteCookie: mockDeleteCookie,
}))

import {emitAuthChanged, reconcileAuthFromCookie, setupAuthSync} from "@/plugins/authSync"

class MockBroadcastChannel {
  static messages: unknown[] = []
  private listeners = new Map<string, Array<() => void>>()

  constructor(_name: string) {}

  postMessage(message: unknown) {
    MockBroadcastChannel.messages.push(message)
  }

  addEventListener(event: string, cb: () => void) {
    const current = this.listeners.get(event) ?? []
    current.push(cb)
    this.listeners.set(event, current)
  }
}

describe("authSync plugin", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    MockBroadcastChannel.messages = []
    Object.defineProperty(globalThis, "BroadcastChannel", {
      configurable: true,
      value: MockBroadcastChannel,
    })
  })

  it("reconciles auth state from cookie", () => {
    mockReadJsonCookie.mockReturnValue({username: "cookie-user", token: "jwt-token"})
    const store = {
      getters: {getLogin: {username: "current-user"}},
      commit: vi.fn(),
    } as unknown as TypedStore

    reconcileAuthFromCookie(store)
    expect(store.commit).toHaveBeenCalledWith("setLoginState", null)
    expect(mockDeleteCookie).toHaveBeenCalledWith("login")
  })

  it("does not commit when auth state matches", () => {
    mockReadJsonCookie.mockReturnValue({username: "same-user"})
    const store = {
      getters: {getLogin: {username: "same-user", token: ""}},
      commit: vi.fn(),
    } as unknown as TypedStore

    reconcileAuthFromCookie(store)
    expect(store.commit).not.toHaveBeenCalled()
  })

  it("emits auth changed ping over storage and broadcast channel", () => {
    const setItem = vi.spyOn(Storage.prototype, "setItem")
    emitAuthChanged()

    expect(setItem).toHaveBeenCalledWith("auth:ping", expect.any(String))
    expect(MockBroadcastChannel.messages.length).toBe(1)
  })

  it("sets up listeners and triggers initial reconcile", () => {
    mockReadJsonCookie.mockReturnValue(null)
    const addStorage = vi.spyOn(window, "addEventListener")
    const addDoc = vi.spyOn(document, "addEventListener")
    const store = {
      getters: {getLogin: null},
      commit: vi.fn(),
    } as unknown as TypedStore

    setupAuthSync(store)

    expect(addStorage).toHaveBeenCalledWith("storage", expect.any(Function))
    expect(addStorage).toHaveBeenCalledWith("focus", expect.any(Function))
    expect(addDoc).toHaveBeenCalledWith("visibilitychange", expect.any(Function))
  })
})
