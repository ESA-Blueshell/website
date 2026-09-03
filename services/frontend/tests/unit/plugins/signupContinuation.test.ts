import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {
  announceAccountActivation,
  forgetSignupToken,
  isSignupTokenRejection,
  notifySignupTokenRejected,
  onAccountActivated,
  onSignupTokenRejected,
  readSignupToken,
  rememberSignupToken,
} from "@/plugins/signupContinuation"

const TOKEN_KEY = "signup:continuation:token"
const MIRROR_KEY = "account:activation:announced"

/**
 * A storage event as another tab's write raises it, carrying only the two fields
 * the listener reads.
 *
 * Assembled rather than through the StorageEvent constructor, whose init
 * dictionary CodeQL models as a superfluous argument. The alert is wrong —
 * lib.dom.d.ts declares a two-parameter constructor — but a helper is where these
 * call sites wanted to be anyway.
 */
function storageWrite(key: string, newValue: string | null): Event {
  return Object.assign(new Event("storage"), {key, newValue})
}

describe("signupContinuation", () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    vi.useFakeTimers()
    vi.setSystemTime(new Date("2026-09-03T12:00:00Z"))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe("the stored token", () => {
    it("keeps the token for this tab and gives it back", () => {
      rememberSignupToken("sel.ver")
      expect(readSignupToken()).toBe("sel.ver")
      expect(sessionStorage.getItem(TOKEN_KEY)).toBe("sel.ver")
    })

    it("has no token before one is stored", () => {
      expect(readSignupToken()).toBeUndefined()
    })

    it("forgets it", () => {
      rememberSignupToken("sel.ver")
      forgetSignupToken()
      expect(readSignupToken()).toBeUndefined()
    })

    // A browser in a privacy mode throws on session storage rather than answering
    // empty, and the step that stored the token went down with it.
    it("survives storage that throws", () => {
      const boom = () => {
        throw new Error("denied")
      }
      // jsdom puts these on the prototype, so the instance is not where they live.
      const getItem = vi.spyOn(Storage.prototype, "getItem").mockImplementation(boom)
      const setItem = vi.spyOn(Storage.prototype, "setItem").mockImplementation(boom)
      const removeItem = vi.spyOn(Storage.prototype, "removeItem").mockImplementation(boom)

      expect(() => rememberSignupToken("sel.ver")).not.toThrow()
      expect(readSignupToken()).toBeUndefined()
      expect(() => forgetSignupToken()).not.toThrow()

      getItem.mockRestore()
      setItem.mockRestore()
      removeItem.mockRestore()
    })
  })

  describe("announcing an activation", () => {
    it("reaches a listener once, though it travels two ways", () => {
      const heard = vi.fn()
      const stop = onAccountActivated(heard)

      announceAccountActivation("lena")
      // The mirror write raises the storage event other tabs hear.
      window.dispatchEvent(storageWrite(MIRROR_KEY, localStorage.getItem(MIRROR_KEY)))

      expect(heard).toHaveBeenCalledTimes(1)
      expect(heard.mock.calls[0]![0]).toMatchObject({username: "lena"})
      stop()
    })

    it("hears a later activation as a new one", () => {
      const heard = vi.fn()
      const stop = onAccountActivated(heard)

      const announce = (at: number) =>
        window.dispatchEvent(storageWrite(MIRROR_KEY, JSON.stringify({at})))

      announce(1000)
      announce(2000)

      expect(heard).toHaveBeenCalledTimes(2)
      stop()
    })

    it("ignores a mirror value it cannot read, and other keys", () => {
      const heard = vi.fn()
      const stop = onAccountActivated(heard)

      window.dispatchEvent(storageWrite(MIRROR_KEY, "not json"))
      window.dispatchEvent(storageWrite("something:else", "{}"))
      window.dispatchEvent(storageWrite(MIRROR_KEY, null))

      expect(heard).not.toHaveBeenCalled()
      stop()
    })

    it("says nothing to a listener that has unsubscribed", () => {
      const heard = vi.fn()
      onAccountActivated(heard)()

      window.dispatchEvent(storageWrite(MIRROR_KEY, JSON.stringify({at: 1})))

      expect(heard).not.toHaveBeenCalled()
    })
  })

  describe("a token the api refuses", () => {
    const refusal = (overrides: Record<string, unknown> = {}) => ({
      response: {data: {code: "RecoveryTokenUnusable"}},
      config: {headers: {"X-Signup-Token": "sel.ver"}},
      ...overrides,
    })

    it("is the refusal this tab has to act on", () => {
      expect(isSignupTokenRejection(refusal())).toBe(true)
    })

    it("does not care how the header was cased", () => {
      expect(
        isSignupTokenRejection(refusal({config: {headers: {"x-signup-token": "sel.ver"}}})),
      ).toBe(true)
    })

    // Another credential's refusal on the same page is not this token's problem.
    it("is not any other refusal on a request carrying the token", () => {
      expect(
        isSignupTokenRejection(refusal({response: {data: {code: "SomethingElse"}}})),
      ).toBe(false)
      expect(isSignupTokenRejection(refusal({response: {data: {}}}))).toBe(false)
    })

    it("is not the same refusal on a request that carried no token", () => {
      expect(isSignupTokenRejection(refusal({config: {headers: {}}}))).toBe(false)
      expect(isSignupTokenRejection(refusal({config: {}}))).toBe(false)
    })

    it("is not something that never reached the api", () => {
      expect(isSignupTokenRejection(new Error("offline"))).toBe(false)
      expect(isSignupTokenRejection(null)).toBe(false)
      expect(isSignupTokenRejection("nope")).toBe(false)
    })

    it("tells whoever is holding the form, until they stop listening", () => {
      const told = vi.fn()
      const stop = onSignupTokenRejected(told)

      notifySignupTokenRejected()
      expect(told).toHaveBeenCalledTimes(1)

      stop()
      notifySignupTokenRejected()
      expect(told).toHaveBeenCalledTimes(1)
    })
  })
})
