import type { AxiosError } from "axios"

// ⬇️ Use the generated schema types from your lib
import type {
  ApiError as ApiErrorSchema,
  FieldValidationError as FieldValidationErrorSchema,
} from "@/lib"

import { useForm } from "vee-validate"

// Shape of the thrown error coming from hey-api axios client.
// We DON’T import the exception class (name varies by version). We narrow by shape,
// while using your generated ApiError schema type for the payload itself.
type HeyApiException = {
  response?: {
    status?: number
    data?: ApiErrorSchema
  }
} & Partial<AxiosError>

// Normalized structure we feed into VeeValidate
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
  const data = err.response?.data as ApiErrorSchema | undefined
  if (!data) return null

  const list = (data as any).errors as FieldValidationErrorSchema[] | undefined
  if (!list?.length) {
    // Only treat as “validation” if it’s clearly a 400 with errors[]
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
export function useBackendValidation(
  objectName?: string,
  fieldMap?: Record<string, string>,
) {
  const { errors, setErrors, setFieldError } = useForm()

  function apply(err: unknown) {
    const parsed = parseApiValidation(err)
    if (!parsed) return false
    if (objectName && parsed.objectName && parsed.objectName !== objectName) {
      return false
    }

    const flat: Record<string, string> = {}
    for (const [backendField, msgs] of Object.entries(parsed.fieldErrors)) {
      const local = fieldMap?.[backendField] ?? backendField
      flat[local] = msgs.join(" ")
    }
    setErrors(flat)
    return true
  }

  // Vuetify friendly — use as :error-messages="err('email')"
  function err(name: string) {
    const msg = (errors.value as any)?.[name]
    return msg ? [msg] : []
  }

  function clear(name: string) {
    setFieldError(name, undefined)
  }

  return { apply, err, clear }
}
