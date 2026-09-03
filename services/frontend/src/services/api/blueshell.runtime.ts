import axios, {AxiosError, AxiosHeaders, type AxiosInstance} from "axios"
import type {Config} from "@/services/api/blueshell/client/types.gen.ts"
import store from "@/plugins/store.ts"
import type {ApiError as ApiErrorSchema} from "@/services/api/blueshell/types.gen.ts"
import {isSignupTokenRejection, notifySignupTokenRejected} from "@/plugins/signupContinuation"

// Vite note: public env vars must be prefixed with VITE_*
export function resolveBaseURL(): string {
  if (import.meta.env.VITE_APP_URL) return import.meta.env.VITE_APP_URL
  // Reasonable dev fallback; avoid https on localhost unless you know it's configured
  if (typeof window !== "undefined") return `${window.location.origin}/api`
  return "https://localhost/api"
}

/**
 * Where a path the api handed back is actually served.
 *
 * The api answers with paths of its own rather than absolute urls, since it has no way of
 * knowing what sits in front of it. A bare path would resolve against the page's origin,
 * which is the frontend and not the api: in production the api answers under `/api` on the
 * same host, and in development on another port entirely.
 */
export function apiUrl(path: string): string {
  if (/^https?:\/\//.test(path)) return path
  return `${resolveBaseURL().replace(/\/$/, "")}${path.startsWith("/") ? path : `/${path}`}`
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
    // We set X-XSRF-TOKEN manually from /csrf response body.
    // Disable Axios auto-cookie XSRF behavior to prevent header override.
    withXSRFToken: false,
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
      // Said once here rather than in each step, which all fail the same way and
      // cannot tell a rejected token from any other refusal.
      if (isSignupTokenRejection(error)) notifySignupTokenRejected()
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
