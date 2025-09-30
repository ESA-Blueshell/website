import { defineRule, configure } from 'vee-validate'
import type { GenericObject } from 'vee-validate'
import { parsePhoneNumber } from 'libphonenumber-js/max'

const isEmpty = (v: unknown) =>
  v === null || v === undefined || (typeof v === 'string' && v.trim() === '')

defineRule('required', (value: unknown) => {
  if (!isEmpty(value)) return true
  return 'This field is required'
})

defineRule('alpha_num', (value: string) => {
  if (isEmpty(value)) return true
  return /^[a-zA-Z0-9]+$/.test(value) || 'Use only letters and numbers'
})

defineRule('min_chars', (value: string, [min]: string[]) => {
  if (isEmpty(value)) return true
  const n = Number(min ?? 0)
  return (value?.length ?? 0) >= n || `Must be at least ${n} characters`
})

defineRule('max_chars', (value: string, [max]: string[]) => {
  if (isEmpty(value)) return true
  const n = Number(max ?? 100)
  return (value?.length ?? 0) <= n || `Must be at most ${n} characters`
})

defineRule('email', (value?: string) => {
  if (isEmpty(value)) return true
  const ok = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(value!)
  return ok || 'Enter a valid e-mail address'
})

defineRule('no_student_email', (value?: string) => {
  if (isEmpty(value)) return true
  return !/student/i.test(value!) || 'You may not use your student email to sign up'
})

defineRule('has_lower', (v: string) => isEmpty(v) || /[a-z]/.test(v) || 'Include a lowercase letter')
defineRule('has_upper', (v: string) => isEmpty(v) || /[A-Z]/.test(v) || 'Include an uppercase letter')
defineRule('has_number', (v: string) => isEmpty(v) || /\d/.test(v) || 'Include a number')
defineRule('has_special', (v: string) => isEmpty(v) || /[@$!%*?&]/.test(v) || 'Include a special char (@$!%*?&)')

// --- Cross-field match (e.g., confirm password) ---
// Usage: rules="required|match:@password"
defineRule('match', (value: string, [other]: string[], ctx) => {
  if (!other?.startsWith('@')) {
    // literal compare
    return value === other || 'Values do not match'
  }
  const otherField = other.slice(1)
  const target = (ctx.form as GenericObject)?.[otherField]
  return value === target || 'Values do not match'
})

// --- Date (yyyy-mm-dd from <input type="date">) ---
defineRule('date_required', (v: string) => !!v || 'Date of birth is required')

// --- Phone (libphonenumber-js) ---
// Usage: rules="required|phone_mobile:NL" or :rules="`required|phone_mobile:${country}`"
defineRule('phone_mobile', (v: string, [country = 'NL']: string[]) => {
  if (isEmpty(v)) return true // let "required" handle empties
  try {
    const pn = parsePhoneNumber(v, country as any)
    if (!pn?.isValid()) return 'Enter a valid phone number'
    return (pn.getType?.() === 'MOBILE') || 'Enter a mobile phone number'
  } catch {
    return 'Enter a valid phone number'
  }
})

configure({
  validateOnInput: true,
})
