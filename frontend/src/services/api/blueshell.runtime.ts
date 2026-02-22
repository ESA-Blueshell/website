import axios, {AxiosError, AxiosHeaders, type AxiosInstance} from "axios"
import type {Config} from "@/services/api/blueshell/client/types.gen.ts"
import store from "@/plugins/store.ts"
import type {ApiError as ApiErrorSchema} from "@/services/api/blueshell/types.gen.ts"

// Vite note: public env vars must be prefixed with VITE_*
function resolveBaseURL(): string {
  if (import.meta.env.VITE_APP_URL) return import.meta.env.VITE_APP_URL
  // Reasonable dev fallback; avoid https on localhost unless you know it's configured
  if (typeof window !== "undefined") return `${window.location.origin}/api`
  return "https://localhost/api"
}

type ApiErrorWithMaybeErrors = ApiErrorSchema & { errors?: unknown }
type CsrfBootstrapResponse = { token?: string }
const CSRF_HEADER_NAME = "X-XSRF-TOKEN"
const CSRF_BOOTSTRAP_PATH = "/csrf"
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"])

let csrfBootstrapPromise: Promise<string | null> | null = null

function isSafeMethod(method: string | undefined): boolean {
  return SAFE_METHODS.has((method ?? "GET").toUpperCase())
}

async function ensureCsrfToken(axiosInstance: AxiosInstance): Promise<string | null> {
  if (!csrfBootstrapPromise) {
    csrfBootstrapPromise = axiosInstance
      .get<CsrfBootstrapResponse>(CSRF_BOOTSTRAP_PATH, {withCredentials: true})
      .then((response) => {
        const bodyToken = typeof response.data?.token === "string" && response.data.token.length > 0
          ? response.data.token
          : null
        store.commit("setXsrfToken", bodyToken)
        return bodyToken
      })
      .catch(() => {
        // Fall back to the last known token if bootstrap transiently fails.
        return store.getters.getXsrfToken
      })
      .finally(() => {
        csrfBootstrapPromise = null
      })
  }

  return await csrfBootstrapPromise
}

function isValidationError(
  data: unknown,
): data is ApiErrorWithMaybeErrors {
  if (!data || typeof data !== "object") return false
  const d = data as Record<string, unknown>
  const status = typeof d.status === "number" ? d.status : undefined
  // Many backends use 400 or 422 for validation errors
  const hasErrorsKey = "errors" in d
  return !!status && (status === 400 || status === 422) && hasErrorsKey
}

export function createClientConfig(defaultConfig: Config): Config {
  const axiosInstance = axios.create({
    baseURL: resolveBaseURL(),
  })

  // Keep auth and CSRF in sync per request.
  axiosInstance.interceptors.request.use(async (cfg) => {
    const token = store.getters.getAuthToken
    const headers = cfg.headers ?? new AxiosHeaders()
    cfg.withCredentials = true

    if (!isSafeMethod(cfg.method)) {
      const csrfToken = await ensureCsrfToken(axiosInstance)
      if (csrfToken) {
        if (headers instanceof AxiosHeaders) headers.set(CSRF_HEADER_NAME, csrfToken)
        else (headers as AxiosHeaders)[CSRF_HEADER_NAME] = csrfToken
      }
    }

    if (token) {
      if (headers instanceof AxiosHeaders) headers.set("Authorization", `Bearer ${token}`)
      else (headers as AxiosHeaders)["Authorization"] = `Bearer ${token}`
    } else if (headers instanceof AxiosHeaders) {
      headers.delete("Authorization")
    } else {
      delete (headers as AxiosHeaders)["Authorization"]
    }

    cfg.headers = headers
    return cfg
  })

  // Normalize validation shape (don’t swallow the error)
  axiosInstance.interceptors.response.use(
    (res) => res,
    (error: AxiosError<ApiErrorSchema>) => {
      const data = error?.response?.data
      if (isValidationError(data)) {
        const errs = (data as ApiErrorSchema).errors
        if (errs && !Array.isArray(errs)) {
          error.response!.data = {...(data as ApiErrorSchema), errors: [errs]} as ApiErrorSchema
        }
      }
      return Promise.reject(error)
    },
  )

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const {baseURL, ...config} = defaultConfig

  // Return config to the generated client
  return {
    ...config,
    axios: axiosInstance,
  } as Config
}
