// vee-validate v4
import {configure, defineRule, type FormContext, type GenericObject} from "vee-validate"
import {type CountryCode, parsePhoneNumber} from "libphonenumber-js/max"
import {DateTime} from "luxon"
import type {AxiosError} from "axios"
import type {ApiError, FieldValidationError} from "@/services/api"

// --- Helpers ---
export const isEmpty = (v: unknown) =>
  v === null || v === undefined || (typeof v === "string" && v.trim() === "")

defineRule("notEmpty", (value: unknown[]) => {
  if (!isEmpty(value)) return true
  return "Must not be empty"
})

defineRule("required", (value: unknown) => {
  if (!isEmpty(value)) return true
  return "This field is required"
})

defineRule("alphaNum", (value: string) => {
  if (isEmpty(value)) return true
  return /^[a-zA-Z0-9]+$/.test(value) || "Use only letters and numbers"
})

defineRule("minChars", (value: string, [min]: string[]) => {
  if (isEmpty(value)) return true
  const n = Number(min ?? 0)
  return (value?.length ?? 0) >= n || `Must be at least ${n} characters`
})

defineRule("maxChars", (value: string, [max]: string[]) => {
  if (isEmpty(value)) return true
  const n = Number(max ?? 100)
  return (value?.length ?? 0) <= n || `Must be at most ${n} characters`
})

defineRule("minValue", (value: string, [min]: string[]) => {
  if (isEmpty(value)) return true
  const minValue = Number(min ?? 0)
  return Number(value) >= minValue || `Must be at least ${minValue}`
})

defineRule("maxValue", (value: string, [max]: string[]) => {
  if (isEmpty(value)) return true
  const maxValue = Number(max ?? 0)
  return Number(value) <= maxValue || `May be at most ${maxValue}`
})

// If you prefer your own email rule over the built-in, keep this and remove the built-in registration above.
defineRule("email", (value?: string) => {
  if (isEmpty(value)) return true
  const ok = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(value!)
  return ok || "Enter a valid e-mail address"
})

defineRule("noStudentEmail", (value?: string) => {
  if (isEmpty(value)) return true
  return !/student/i.test(value!) || "You may not use your student email to sign up"
})

defineRule("hasLower", (v: string) => isEmpty(v) || /[a-z]/.test(v) || "Include a lowercase letter")
defineRule("hasUpper", (v: string) => isEmpty(v) || /[A-Z]/.test(v) || "Include an uppercase letter")
defineRule("hasNumber", (v: string) => isEmpty(v) || /\d/.test(v) || "Include a number")
defineRule("hasSpecial", (v: string) => isEmpty(v) || /[@$!%*?&]/.test(v) || "Include a special char (@$!%*?&)")

// --- Cross-field match (e.g., confirm password) ---
// Usage: rules="required|match:@password"
defineRule("match", (value: string, [other]: string[], ctx) => {
  if (isEmpty(value)) return true
  if (!other?.startsWith("@")) {
    // literal compare
    return value === other || "Values do not match"
  }
  const otherField = other.slice(1)
  const target = (ctx.form as GenericObject)?.[otherField]
  return value === target || "Values do not match"
})

// --- Date comparisons (ISO yyyy-mm-dd) ---
// Note: your original code compared `value` to itself when `other` was literal.
// That makes the rule always fail to signal the intended check; fixed below.
defineRule("dateBefore", (value: string, [other]: string[], ctx) => {
  if (isEmpty(value)) return true
  const dateTimeValue = DateTime.fromISO(value)

  let dateTimeTarget: DateTime
  if (!other?.startsWith("@")) {
    dateTimeTarget = DateTime.fromISO(other!)
  } else {
    const otherField = other.slice(1)
    const target = (ctx.form as GenericObject)?.[otherField]
    dateTimeTarget = DateTime.fromISO(target)
  }

  if (!dateTimeValue.isValid) return "Enter a valid date"
  if (!dateTimeTarget.isValid) return true
  return dateTimeValue < dateTimeTarget || `Date must be before ${dateTimeTarget.toISODate()}`
})

defineRule("dateAfter", (value: string, [other]: string[], ctx) => {
  if (isEmpty(value)) return true
  const dateTimeValue = DateTime.fromISO(value)

  let dateTimeTarget: DateTime
  if (!other?.startsWith("@")) {
    dateTimeTarget = DateTime.fromISO(other!)
  } else {
    const otherField = other.slice(1)
    const target = (ctx.form as GenericObject)?.[otherField]
    dateTimeTarget = DateTime.fromISO(target)
  }

  if (!dateTimeValue.isValid) return "Enter a valid date"
  if (!dateTimeTarget.isValid) return true
  return dateTimeValue > dateTimeTarget || `Date must be after ${dateTimeTarget.toISODate()}`
})

defineRule("dateMax", (value: string, [other]: string[], ctx) => {
  if (isEmpty(value)) return true
  const dateTimeValue = DateTime.fromISO(value)

  let dateTimeTarget: DateTime
  if (!other?.startsWith("@")) {
    dateTimeTarget = DateTime.fromISO(other!)
  } else {
    const otherField = other.slice(1)
    const target = (ctx.form as GenericObject)?.[otherField]
    dateTimeTarget = DateTime.fromISO(target)
  }

  if (!dateTimeValue.isValid) return "Enter a valid date"
  if (!dateTimeTarget.isValid) return true
  return dateTimeValue <= dateTimeTarget || `Date must be at most ${dateTimeTarget.toISODate()}`
})

defineRule("dateMin", (value: string, [other]: string[], ctx) => {
  if (isEmpty(value)) return true
  const dateTimeValue = DateTime.fromISO(value)

  let dateTimeTarget: DateTime
  if (!other?.startsWith("@")) {
    dateTimeTarget = DateTime.fromISO(other!)
  } else {
    const otherField = other.slice(1)
    const target = (ctx.form as GenericObject)?.[otherField]
    dateTimeTarget = DateTime.fromISO(target)
  }

  if (!dateTimeValue.isValid) return "Enter a valid date"
  if (!dateTimeTarget.isValid) return true
  return dateTimeValue >= dateTimeTarget || `Date must be at least ${dateTimeTarget.toISODate()}`
})

// --- Date required (from <input type="date">) ---
defineRule("dateRequired", (v: string) => !!v || "Date is required")

// --- Phone (libphonenumber-js) ---
// Usage: rules="required|phone_mobile:NL" or :rules="`required|phone_mobile:${country}`"
defineRule("phoneMobile", (v: string, [country = "NL"]: string[]) => {
  if (isEmpty(v)) return true // let "required" handle empties
  try {
    const pn = parsePhoneNumber(v, country as CountryCode)
    if (!pn?.isValid()) return "Enter a valid phone number"
    const t = pn.getType?.()
    // Some regions return "FIXED_LINE_OR_MOBILE"; accept that as mobile-friendly.
    return (t === "MOBILE" || t === "FIXED_LINE_OR_MOBILE") || "Enter a mobile phone number"
  } catch {
    return "Enter a valid phone number"
  }
})

defineRule("dateTimeAfter", (value: string, [other]: string[], ctx) => {
  if (isEmpty(value)) return true
  const dateTimeValue = DateTime.fromISO(value)

  let dateTimeTarget: DateTime
  if (!other?.startsWith("@")) {
    dateTimeTarget = DateTime.fromISO(other!)
  } else {
    const otherField = other.slice(1)
    const target = (ctx.form as GenericObject)?.[otherField]
    dateTimeTarget = DateTime.fromISO(target)
  }

  if (!dateTimeValue.isValid) return "Enter a valid date"
  if (!dateTimeTarget.isValid) return true
  return dateTimeValue >= dateTimeTarget || `Must be after ${dateTimeTarget.toFormat("dd/MM/yyyy HH:mm")}`
})

defineRule("dateTimeNotAfter", (value: string, [other]: string[], ctx) => {
  if (isEmpty(value)) return true
  const dateTimeValue = DateTime.fromISO(value)

  let dateTimeTarget: DateTime
  if (!other?.startsWith("@")) {
    dateTimeTarget = DateTime.fromISO(other!)
  } else {
    const otherField = other.slice(1)
    const target = (ctx.form as GenericObject)?.[otherField]
    dateTimeTarget = DateTime.fromISO(target)
  }

  if (!dateTimeValue.isValid) return "Enter a valid date"
  if (!dateTimeTarget.isValid) return true
  return dateTimeValue <= dateTimeTarget || `Must be before or on ${dateTimeTarget.toFormat("dd/MM/yyyy HH:mm")}`
})

// --- Global config ---
configure({
  validateOnInput: true,
})

type HeyApiException = {
  response?: {
    status?: number
    data?: ApiError
  }
} & Partial<AxiosError>

export type ParsedValidation = {
  objectName?: string | null
  fieldErrors: Record<string, string[]>
  detail?: string | null
  status?: number
  traceId?: string | null
}


function isHeyApiError(e: unknown): e is HeyApiException {
  return !!(e && typeof e === "object" && (e as HeyApiException).response?.status)
}

export function parseApiValidation(err: unknown): ParsedValidation | null {
  if (!isHeyApiError(err)) return null
  const data = err.response?.data as ApiError
  if (!data) return null

  const list = (data.errors || []) as FieldValidationError[]
  if (!list?.length) {
    if (err.response?.status !== 400) return null
    return null
  }

  const out: ParsedValidation = {
    objectName: list[0]?.objectName ?? null,
    fieldErrors: {},
    detail: data.detail ?? null,
    status: data.status,
    traceId: data.traceId ?? null,
  }

  for (const fe of list) {
    const name = fe.field
    if (!name) continue
    if (!out.fieldErrors[name]) out.fieldErrors[name] = []
    out.fieldErrors[name].push(fe.message!)
  }
  return out
}

/**
 * Maps backend field paths to one or more frontend VeeValidate field names.
 * e.g. { startTime: ["startDate", "startTime"], "banner.fileId": "banner" }
 */
export type FieldMap = Record<string, string | string[]>

/** What `apply` could not put on a field, so a caller can still say it out loud. */
export type UnattachedErrors = {
  /** Messages for fields this form does not render, in `field: message` form. */
  messages: string[]
  /** The response's own summary, when it carried one. */
  detail?: string | null
}

/** Every path this form has a field for, including the nested ones. */
function knownPaths(values: unknown, prefix = "", into = new Set<string>()): Set<string> {
  if (!values || typeof values !== "object" || Array.isArray(values)) return into
  for (const [key, value] of Object.entries(values as Record<string, unknown>)) {
    const path = prefix ? `${prefix}.${key}` : key
    into.add(path)
    knownPaths(value, path, into)
  }
  return into
}

/**
 * Which of this form's fields a backend field path belongs to.
 *
 * The two ends name the same field differently. A constraint on a nested request
 * object is reported under its whole path (`memberProfile.nationality`) while the
 * form that collected it renders one flat field (`nationality`), and a body that
 * omitted the field fails in the deserializer instead, which knows only the leaf.
 * Trying the path and then the leaf covers all three without the form having to
 * enumerate them.
 */
function resolveTargets(field: string, fieldMap: FieldMap | undefined, paths: Set<string>): string[] {
  const mapped = fieldMap?.[field]
  if (mapped != null) return Array.isArray(mapped) ? mapped : [mapped]
  if (paths.has(field)) return [field]
  const leaf = field.slice(field.lastIndexOf(".") + 1)
  return paths.has(leaf) ? [leaf] : []
}

/**
 * Applies backend validation errors to the current VeeValidate <Form>.
 * Pass an optional fieldMap to translate backend property paths to frontend field names.
 * One backend field can map to multiple frontend fields (useful for split date/time inputs).
 *
 * Returns null when the error is not a field-validation response, and otherwise
 * what it could not attach. VeeValidate parks an error for a field the form does
 * not render in a bag that nothing renders, so reporting the attachment is what
 * keeps a rejection the form cannot show from passing as one it did.
 */
export function apply(
  formContext: FormContext,
  err: unknown,
  fieldMap?: FieldMap
): UnattachedErrors | null {
  const parsed = parseApiValidation(err)
  if (!parsed) return null

  const paths = knownPaths(formContext.values)
  const messages: string[] = []

  for (const [field, msgs] of Object.entries(parsed.fieldErrors)) {
    const targets = resolveTargets(field, fieldMap, paths)
    if (!targets.length) {
      for (const msg of msgs) messages.push(`${field}: ${msg}`)
      continue
    }
    for (const target of targets) formContext.setFieldError(target, msgs)
  }

  return {messages, detail: parsed.detail}
}
