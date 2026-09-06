/**
 * What a job's payload is allowed to show on a row.
 *
 * A payload is whatever the api put in it, so this decides by rule rather than by field list:
 * anything token-shaped never reaches the screen, and a key the row already shows as a resolved
 * related entity is not shown twice. It is a module of its own rather than a page's helper
 * because a redaction rule nothing can test without a browser is a redaction rule nobody checks.
 */

/** One payload field as a row draws it. */
export interface PayloadChip {
  key: string
  label: string
  value: string
}

/**
 * Keys the row already shows resolved, as a related entity with a name on it, plus the "unused"
 * sentinel a zero-argument payload carries. Compared lower-case, so casing cannot smuggle one past.
 */
export const SUPPRESSED_PAYLOAD_KEYS = new Set([
  "userid",
  "eventid",
  "eventsignupid",
  "periodid",
  "contributionperiodid",
  "cohortid",
  "unused",
])

/**
 * A key whose value is a secret. Matched by substring rather than by name: a payload field added
 * to the api tomorrow gets redacted without anyone editing this list, which is the way round that
 * fails safe.
 */
export function isSensitiveKey(key: string): boolean {
  const lower = key.toLowerCase()
  return lower.includes("token")
    || lower.includes("secret")
    || lower.includes("password")
    || lower.includes("apikey")
    || lower === "key"
}

/** A value with nothing in it to read: absent, blank, or an empty object from a Unit payload. */
export function isUninterestingValue(value: unknown): boolean {
  if (value == null) return true
  if (typeof value === "string") return value.trim() === ""
  if (typeof value === "object") return Object.keys(value as Record<string, unknown>).length === 0
  return false
}

const titleCaseToken = (value: string): string =>
  value.charAt(0).toUpperCase() + value.slice(1).toLowerCase()

/** `contributionPeriodId` reads as `Contribution Period Id`: a field name said out loud. */
export function humanizeFieldName(name: string): string {
  return name
    .replace(/([A-Z])/g, " $1")
    .replace(/[._-]+/g, " ")
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map(titleCaseToken)
    .join(" ")
}

export function formatPayloadValue(value: unknown): string {
  if (value == null) return "—"
  if (typeof value === "string") return value
  if (typeof value === "number" || typeof value === "boolean") return String(value)
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

/** The chips one job's payload earns, in the order the api wrote the fields. */
export function payloadChips(payload: unknown): PayloadChip[] {
  if (!payload || typeof payload !== "object") return []
  return Object.entries(payload as Record<string, unknown>)
    .filter(([key, value]) =>
      !SUPPRESSED_PAYLOAD_KEYS.has(key.toLowerCase())
      && !isSensitiveKey(key)
      && !isUninterestingValue(value))
    .map(([key, value]) => ({key, label: humanizeFieldName(key), value: formatPayloadValue(value)}))
}
