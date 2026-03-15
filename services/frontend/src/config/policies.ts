export const ACTIVE_COOKIE_POLICY_NAME = "20260223 - ESA Blueshell Cookie Policy"

export const ACTIVE_COOKIE_POLICY_PATHS = {
  english: "@/assets/documents/20260223 - ESA Blueshell Cookie Policy.pdf",
  dutch: "@/assets/documents/20260223 - ESA Blueshell Cookiebeleid.pdf",
} as const

export const ACTIVE_COOKIE_POLICY_DOWNLOAD_NAMES = {
  english: "ESA Blueshell - Cookie Policy.pdf",
  dutch: "ESA Blueshell - Cookiebeleid.pdf",
} as const

export const COOKIE_CONSENT_STORAGE_KEY = "esa-blueshell.nl:cookiesAccepted"

export type CookieConsentPayload = {
  policyName: string
  acceptedAt?: string
}

const isCookieConsentPayload = (value: unknown): value is CookieConsentPayload => {
  if (!value || typeof value !== "object") {
    return false
  }

  const payload = value as Record<string, unknown>
  return typeof payload.policyName === "string"
}

export const decodeCookieConsentPayload = (raw: string | null): CookieConsentPayload | null => {
  if (!raw) {
    return null
  }

  if (raw === "true") {
    return null
  }

  try {
    const parsed: unknown = JSON.parse(raw)
    if (isCookieConsentPayload(parsed)) {
      return parsed
    }
  } catch {
    // Backward-compatible fallback for non-JSON values.
  }

  return {policyName: raw}
}

export const encodeCookieConsentPayload = (
  policyName: string = ACTIVE_COOKIE_POLICY_NAME,
): string => JSON.stringify({
  policyName,
  acceptedAt: new Date().toISOString(),
} satisfies CookieConsentPayload)

export const hasAcceptedCookiePolicy = (
  raw: string | null,
  policyName: string = ACTIVE_COOKIE_POLICY_NAME,
): boolean => decodeCookieConsentPayload(raw)?.policyName === policyName
