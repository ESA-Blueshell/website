import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  ACTIVE_COOKIE_POLICY_NAME,
  COOKIE_CONSENT_STORAGE_KEY,
  encodeCookieConsentPayload,
} from "@/config/policies"

vi.mock("@/plugins/require.ts", () => ({
  $require: (path: string) => path,
}))

import {useCookiePolicyConsent} from "@/composables/useCookiePolicyConsent"

describe("useCookiePolicyConsent", () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it("returns showCookieSnackbar = true when no consent in localStorage", () => {
    const {showCookieSnackbar, refreshCookieConsentPrompt} = useCookiePolicyConsent()
    refreshCookieConsentPrompt()

    expect(showCookieSnackbar.value).toBe(true)
  })

  it("returns showCookieSnackbar = false after acceptCookies is called", () => {
    const {showCookieSnackbar, refreshCookieConsentPrompt, acceptCookies} = useCookiePolicyConsent()
    refreshCookieConsentPrompt()
    expect(showCookieSnackbar.value).toBe(true)

    acceptCookies()

    expect(showCookieSnackbar.value).toBe(false)
  })

  it("acceptCookies writes correct payload to localStorage", () => {
    const {acceptCookies} = useCookiePolicyConsent()
    acceptCookies()

    const stored = localStorage.getItem(COOKIE_CONSENT_STORAGE_KEY)
    expect(stored).not.toBeNull()

    const parsed = JSON.parse(stored!)
    expect(parsed.policyName).toBe(ACTIVE_COOKIE_POLICY_NAME)
    expect(typeof parsed.acceptedAt).toBe("string")
  })

  it("refreshCookieConsentPrompt re-evaluates consent state", () => {
    const {showCookieSnackbar, refreshCookieConsentPrompt} = useCookiePolicyConsent()

    refreshCookieConsentPrompt()
    expect(showCookieSnackbar.value).toBe(true)

    localStorage.setItem(COOKIE_CONSENT_STORAGE_KEY, encodeCookieConsentPayload())
    refreshCookieConsentPrompt()
    expect(showCookieSnackbar.value).toBe(false)
  })

  it("shows snackbar when stored consent is for a different policy", () => {
    localStorage.setItem(
      COOKIE_CONSENT_STORAGE_KEY,
      encodeCookieConsentPayload("Old Policy Name"),
    )

    const {showCookieSnackbar, refreshCookieConsentPrompt} = useCookiePolicyConsent()
    refreshCookieConsentPrompt()

    expect(showCookieSnackbar.value).toBe(true)
  })
})
