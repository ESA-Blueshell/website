import {type CountryCode, parsePhoneNumber} from "libphonenumber-js/max"
import {DateTime} from "luxon"

/** What a field is checked against: a sentence saying what is wrong, or null where nothing is. */
export type Check = (value: never) => string | null

type Other = () => string | null | undefined

export const isEmpty = (value: unknown): boolean =>
  value === null || value === undefined || (typeof value === "string" && value.trim() === "")

/** The first failure among a field's checks, in the order they are given. */
export function firstFailure(value: unknown, checks: readonly Check[]): string | null {
  for (const check of checks) {
    const failure = (check as (value: unknown) => string | null)(value)
    if (failure) return failure
  }
  return null
}

// Every check but the ones about emptiness passes an empty field, which is required's to refuse.
const unlessEmpty = (test: (value: string) => string | null) => (value: unknown): string | null =>
  (isEmpty(value) ? null : test(String(value)))

export const required = (value: unknown): string | null => (isEmpty(value) ? "This field is required" : null)

export const notEmpty = (value: unknown): string | null => (isEmpty(value) ? "Must not be empty" : null)

export const dateRequired = (value: unknown): string | null => (value ? null : "Date is required")

export const accepted = (message: string) => (value: unknown): string | null => (value === true ? null : message)

export const alphaNum = unlessEmpty(value => (/^[a-zA-Z0-9]+$/.test(value) ? null : "Use only letters and numbers"))

export const email = unlessEmpty(value =>
  (/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(value) ? null : "Enter a valid e-mail address"))

export const minChars = (min: number) =>
  unlessEmpty(value => (value.length >= min ? null : `Must be at least ${min} characters`))

export const maxChars = (max: number) =>
  unlessEmpty(value => (value.length <= max ? null : `Must be at most ${max} characters`))

export const minValue = (min: number) => unlessEmpty(value => (Number(value) >= min ? null : `Must be at least ${min}`))

export const maxValue = (max: number) => unlessEmpty(value => (Number(value) <= max ? null : `May be at most ${max}`))

// Anything that is not a letter or a digit counts as special, as the api's PasswordPolicy has it.
const PASSWORD_NEEDS: [RegExp, string][] = [
  [/[a-z]/, "Include a lowercase letter"],
  [/[A-Z]/, "Include an uppercase letter"],
  [/\d/, "Include a number"],
  [/[^A-Za-z\d]/, "Include a special character"],
]

export const strongPassword = unlessEmpty(value =>
  PASSWORD_NEEDS.find(([pattern]) => !pattern.test(value))?.[1] ?? null)

export const matches = (other: Other) => unlessEmpty(value => (value === other() ? null : "Values do not match"))

/** A date set against another, read when the check runs; a target that is not a date passes. */
const againstDate = (holds: (value: DateTime, target: DateTime) => boolean, says: (target: DateTime) => string) =>
  (other: Other) => unlessEmpty(value => {
    const read = DateTime.fromISO(value)
    if (!read.isValid) return "Enter a valid date"
    const target = DateTime.fromISO(other() ?? "")
    if (!target.isValid) return null
    return holds(read, target) ? null : says(target)
  })

export const dateBefore = againstDate((value, target) => value < target, target => `Date must be before ${target.toISODate()}`)
export const dateAfter = againstDate((value, target) => value > target, target => `Date must be after ${target.toISODate()}`)
export const dateMax = againstDate((value, target) => value <= target, target => `Date must be at most ${target.toISODate()}`)
export const dateMin = againstDate((value, target) => value >= target, target => `Date must be at least ${target.toISODate()}`)

const MOMENT = "dd/MM/yyyy HH:mm"
export const momentAfter = againstDate((value, target) => value >= target, target => `Must be after ${target.toFormat(MOMENT)}`)
export const momentNotAfter = againstDate(
  (value, target) => value <= target,
  target => `Must be before or on ${target.toFormat(MOMENT)}`,
)

/** A mobile number in the country it is read in; some regions only say it may be mobile, which counts. */
export const phoneMobile = (country: () => string) => unlessEmpty(value => {
  try {
    const number = parsePhoneNumber(value, country() as CountryCode)
    if (!number?.isValid()) return "Enter a valid phone number"
    const type = number.getType?.()
    return type === "MOBILE" || type === "FIXED_LINE_OR_MOBILE" ? null : "Enter a mobile phone number"
  } catch {
    return "Enter a valid phone number"
  }
})
