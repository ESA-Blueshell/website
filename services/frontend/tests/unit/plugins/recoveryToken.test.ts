import {beforeEach, describe, expect, it, vi} from "vitest"
import {clearStoredRecoveryToken, loadRecoveryTokenFromRoute} from "@/plugins/recoveryToken"
import type {RouteLocationNormalizedLoaded, Router} from "vue-router"

const STORAGE_KEY = "test:recoveryToken"

function fakeRoute(overrides: {
  hash?: string
  query?: Record<string, string>
} = {}): RouteLocationNormalizedLoaded {
  return {
    hash: overrides.hash ?? "",
    query: overrides.query ?? {},
    params: {},
    path: "/reset",
    name: "reset",
    fullPath: "/reset",
    matched: [],
    meta: {},
    redirectedFrom: undefined,
  } as unknown as RouteLocationNormalizedLoaded
}

function fakeRouter(): Router {
  return {
    replace: vi.fn().mockResolvedValue(undefined),
  } as unknown as Router
}

describe("recoveryToken plugin", () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  describe("loadRecoveryTokenFromRoute", () => {
    it("extracts token from hash fragment", () => {
      const route = fakeRoute({hash: "#token=abc123"})
      const router = fakeRouter()

      const result = loadRecoveryTokenFromRoute(route, router, STORAGE_KEY)

      expect(result).toBe("abc123")
      expect(sessionStorage.getItem(STORAGE_KEY)).toBe("abc123")
      expect(router.replace).toHaveBeenCalled()
    })

    it("extracts token from query parameter", () => {
      const route = fakeRoute({query: {token: "qry-token"}})
      const router = fakeRouter()

      const result = loadRecoveryTokenFromRoute(route, router, STORAGE_KEY)

      expect(result).toBe("qry-token")
      expect(sessionStorage.getItem(STORAGE_KEY)).toBe("qry-token")
    })

    it("prefers hash token over query token", () => {
      const route = fakeRoute({
        hash: "#token=hash-token",
        query: {token: "query-token"},
      })
      const router = fakeRouter()

      const result = loadRecoveryTokenFromRoute(route, router, STORAGE_KEY)

      expect(result).toBe("hash-token")
    })

    it("reads from sessionStorage when route has no token", () => {
      sessionStorage.setItem(STORAGE_KEY, "stored-token")

      const route = fakeRoute()
      const router = fakeRouter()

      const result = loadRecoveryTokenFromRoute(route, router, STORAGE_KEY)

      expect(result).toBe("stored-token")
      expect(router.replace).not.toHaveBeenCalled()
    })

    it("returns empty string when no token anywhere", () => {
      const route = fakeRoute()
      const router = fakeRouter()

      const result = loadRecoveryTokenFromRoute(route, router, STORAGE_KEY)

      expect(result).toBe("")
    })

    it("strips token from URL after extraction", () => {
      const route = fakeRoute({hash: "#token=secret&other=keep"})
      const router = fakeRouter()

      loadRecoveryTokenFromRoute(route, router, STORAGE_KEY)

      expect(router.replace).toHaveBeenCalledWith(
        expect.objectContaining({
          hash: "#other=keep",
        }),
      )
    })
  })

  describe("clearStoredRecoveryToken", () => {
    it("removes token from sessionStorage", () => {
      sessionStorage.setItem(STORAGE_KEY, "some-token")

      clearStoredRecoveryToken(STORAGE_KEY)

      expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull()
    })
  })
})
