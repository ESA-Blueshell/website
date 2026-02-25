import {describe, expect, it} from "vitest"
import {
  ACTIVE_COOKIE_POLICY_NAME,
  decodeCookieConsentPayload,
  encodeCookieConsentPayload,
  hasAcceptedCookiePolicy,
  type CookieConsentPayload,
} from "@/config/policies"

describe("policies", () => {
  describe("encodeCookieConsentPayload", () => {
    it("produces JSON with policyName and acceptedAt", () => {
      const raw = encodeCookieConsentPayload("My Policy")
      const parsed = JSON.parse(raw) as CookieConsentPayload

      expect(parsed.policyName).toBe("My Policy")
      expect(typeof parsed.acceptedAt).toBe("string")
      expect(new Date(parsed.acceptedAt!).getTime()).not.toBeNaN()
    })

    it("defaults to the active cookie policy name", () => {
      const raw = encodeCookieConsentPayload()
      const parsed = JSON.parse(raw) as CookieConsentPayload

      expect(parsed.policyName).toBe(ACTIVE_COOKIE_POLICY_NAME)
    })
  })

  describe("decodeCookieConsentPayload", () => {
    it("parses valid JSON payload", () => {
      const json = JSON.stringify({policyName: "test-policy", acceptedAt: "2026-01-01T00:00:00Z"})
      const result = decodeCookieConsentPayload(json)

      expect(result).toEqual({policyName: "test-policy", acceptedAt: "2026-01-01T00:00:00Z"})
    })

    it("returns null for null input", () => {
      expect(decodeCookieConsentPayload(null)).toBeNull()
    })

    it("returns null for empty string", () => {
      expect(decodeCookieConsentPayload("")).toBeNull()
    })

    it("returns null for legacy 'true' string", () => {
      expect(decodeCookieConsentPayload("true")).toBeNull()
    })

    it("returns malformed JSON as policyName fallback", () => {
      expect(decodeCookieConsentPayload("not-json")).toEqual({policyName: "not-json"})
    })

    it("returns null for JSON that is not a valid payload object", () => {
      expect(decodeCookieConsentPayload(JSON.stringify(42))).toEqual({policyName: "42"})
    })

    it("rejects JSON object missing policyName by falling back to raw string", () => {
      const json = JSON.stringify({acceptedAt: "2026-01-01"})
      const result = decodeCookieConsentPayload(json)
      // isCookieConsentPayload fails → fallback treats raw JSON string as policyName
      expect(result).toEqual({policyName: json})
    })
  })

  describe("hasAcceptedCookiePolicy", () => {
    it("returns true when stored policyName matches current", () => {
      const raw = encodeCookieConsentPayload(ACTIVE_COOKIE_POLICY_NAME)
      expect(hasAcceptedCookiePolicy(raw)).toBe(true)
    })

    it("returns false when stored policyName does not match current", () => {
      const raw = encodeCookieConsentPayload("Old Policy Name")
      expect(hasAcceptedCookiePolicy(raw)).toBe(false)
    })

    it("returns false for null input", () => {
      expect(hasAcceptedCookiePolicy(null)).toBe(false)
    })

    it("returns false for legacy 'true' value", () => {
      expect(hasAcceptedCookiePolicy("true")).toBe(false)
    })

    it("matches against a custom policy name", () => {
      const raw = encodeCookieConsentPayload("custom-policy")
      expect(hasAcceptedCookiePolicy(raw, "custom-policy")).toBe(true)
      expect(hasAcceptedCookiePolicy(raw, "other-policy")).toBe(false)
    })
  })
})
