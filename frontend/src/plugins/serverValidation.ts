import type { AxiosError } from "axios"

// ⬇️ Use the generated schema types from your lib
import type {
  ApiError,
  FieldValidationError,
} from "@/lib"

import {type FormContext, useForm} from "vee-validate"

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
  return !!(e && typeof e === "object" && (e as any).response?.status)
}

export function parseApiValidation(err: unknown): ParsedValidation | null {
  if (!isHeyApiError(err)) return null
  const data = err.response?.data as ApiError | undefined
  if (!data) return null

  const list = (data as any).errors as FieldValidationError[] | undefined
  if (!list?.length) {
    if (err.response?.status !== 400) return null
    return null
  }

  const out: ParsedValidation = {
    objectName: list[0]?.objectName ?? null,
    fieldErrors: {},
    detail: (data as any).detail ?? null,
    status: (data as any).status,
    traceId: (data as any).traceId ?? null,
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
 * Composable to apply backend validation to current VeeValidate <Form>.
 * Optionally pass:
 *  - objectName: only apply errors matching this DTO name
 *  - fieldMap:   map backend field -> local field (e.g., { last_name: "lastName" })
 */
export function useBackendValidation() {

  function apply(formContext: FormContext, err: unknown) {
    const parsed = parseApiValidation(err)
    if (!parsed) return false

    for (const [field, msgs] of Object.entries(parsed.fieldErrors)) {
      console.log("field:", field, "errors:", msgs)
      formContext.setFieldError(field, msgs)
    }
    return true
  }

  return { apply }
}
