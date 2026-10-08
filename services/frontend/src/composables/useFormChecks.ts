import {computed, ref, type ComputedRef} from "vue"
import type {AxiosError} from "axios"
import type {ApiError, FieldValidationError} from "@/services/api"
import {$handleNetworkError, $showStatusMessage} from "@/plugins/handleNetworkError"
import {type Check, firstFailure} from "@/utils/checks"

export type FieldChecks = {value: () => unknown; checks: readonly Check[]}

/**
 * Maps an api field path to the form field, or fields, that show it, as
 * `{startTime: ["startDate", "startTime"], "banner.fileId": "banner"}`. A path is never guessed
 * from its last segment: `memberProfile.country` and an address's `country` are different fields.
 */
export type FieldMap = Record<string, string | string[]>

export type ParsedValidation = {
  objectName?: string | null
  fieldErrors: Record<string, string[]>
  globalErrors: string[]
  detail?: string | null
  status?: number
  traceId?: string | null
}

type Refused = {messages: string[]; value: string}

type HeyApiException = {response?: {status?: number; data?: ApiError}} & Partial<AxiosError>

const isHeyApiError = (error: unknown): error is HeyApiException =>
  !!(error && typeof error === "object" && (error as HeyApiException).response?.status)

/** The field refusals of an api answer, or null where it is not one. */
export function parseApiValidation(error: unknown): ParsedValidation | null {
  if (!isHeyApiError(error)) return null
  const data = error.response?.data
  const list = (data?.errors ?? []) as FieldValidationError[]
  if (!data || list.length === 0) return null
  const parsed: ParsedValidation = {
    objectName: list[0]?.objectName ?? null,
    fieldErrors: {},
    globalErrors: [],
    detail: data.detail ?? null,
    status: data.status,
    traceId: data.traceId ?? null,
  }
  for (const one of list) {
    if (!one.field) {
      if (one.message) parsed.globalErrors.push(one.message)
      continue
    }
    parsed.fieldErrors[one.field] = [...(parsed.fieldErrors[one.field] ?? []), one.message!]
  }
  return parsed
}

const snapshot = (value: unknown): string => JSON.stringify(value ?? null)

/**
 * A form's checks: each field's failure shows once the field was left or a save was tried, and
 * a refusal from the api stays on its field until the field changes or a save is tried again.
 */
export function useFormChecks(fields: () => Record<string, FieldChecks>) {
  const touched = ref(new Set<string>())
  const tried = ref(false)
  const refused = ref<Record<string, Refused>>({})

  const failures: ComputedRef<Record<string, string | null>> = computed(() =>
    Object.fromEntries(Object.entries(fields()).map(([name, field]) => [name, firstFailure(field.value(), field.checks)])))

  const standingRefusal = (name: string): string[] => {
    const held = refused.value[name]
    const field = fields()[name]
    return held && field && held.value === snapshot(field.value()) ? held.messages : []
  }

  const errorsOf = (name: string): string[] => {
    const refusal = standingRefusal(name)
    if (refusal.length > 0) return refusal
    const failure = failures.value[name]
    return failure && (tried.value || touched.value.has(name)) ? [failure] : []
  }

  const valid = computed(() =>
    Object.keys(fields()).every(name => failures.value[name] === null && standingRefusal(name).length === 0))

  const touch = (name: string) => {
    touched.value = new Set(touched.value).add(name)
  }

  /** Starts a save: earlier refusals go, every failure shows, and the answer says whether it may go. */
  const attempt = (): boolean => {
    refused.value = {}
    tried.value = true
    return valid.value
  }

  const settle = () => {
    refused.value = {}
    tried.value = false
    touched.value = new Set()
  }

  /** Puts the api's field refusals on the fields that show them, and answers what landed on none. */
  const refuse = (error: unknown, fieldMap?: FieldMap): {messages: string[]} | null => {
    const parsed = parseApiValidation(error)
    if (!parsed) return null
    const shown = fields()
    const messages = [...parsed.globalErrors]
    const placed: Record<string, Refused> = {}
    for (const [path, said] of Object.entries(parsed.fieldErrors)) {
      const targets = [fieldMap?.[path] ?? path].flat().filter(target => target in shown)
      if (targets.length === 0) messages.push(...said)
      for (const target of targets) placed[target] = {messages: said, value: snapshot(shown[target]!.value())}
    }
    refused.value = {...refused.value, ...placed}
    return {messages}
  }

  return {errorsOf, valid, touch, attempt, settle, refuse}
}

export type FormChecks = ReturnType<typeof useFormChecks>

/**
 * Shows a refused save on its form and says out loud what lands on no field, so a refusal is never
 * silent; anything that is not a field refusal goes to the network handler. Answers which it was.
 */
export function reportRefusal(form: Pick<FormChecks, "refuse">, error: unknown, fieldMap?: FieldMap): boolean {
  const left = form.refuse(error, fieldMap)
  if (!left) {
    $handleNetworkError(error)
    return false
  }
  if (left.messages.length > 0) $showStatusMessage(left.messages.join(" "))
  return true
}
