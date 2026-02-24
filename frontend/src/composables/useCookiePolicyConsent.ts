import {ref} from "vue"
import {$require} from "@/plugins/require.ts"
import {
  ACTIVE_COOKIE_POLICY_PATHS,
  COOKIE_CONSENT_STORAGE_KEY,
  encodeCookieConsentPayload,
  hasAcceptedCookiePolicy,
} from "@/config/policies"

export const useCookiePolicyConsent = () => {
  const showCookieSnackbar = ref(false)
  const activeCookiePolicyUrl = $require(ACTIVE_COOKIE_POLICY_PATHS.english)

  const refreshCookieConsentPrompt = () => {
    const storedValue = localStorage.getItem(COOKIE_CONSENT_STORAGE_KEY)
    showCookieSnackbar.value = !hasAcceptedCookiePolicy(storedValue)
  }

  const acceptCookies = () => {
    localStorage.setItem(COOKIE_CONSENT_STORAGE_KEY, encodeCookieConsentPayload())
    showCookieSnackbar.value = false
  }

  return {
    activeCookiePolicyUrl,
    showCookieSnackbar,
    refreshCookieConsentPrompt,
    acceptCookies,
  }
}

