import axios, { AxiosError, AxiosHeaders } from "axios"
import type { Config } from "@/lib/blueshell/client/types.gen.ts"
import store from "@/plugins/store.ts"
import type { ApiError as ApiErrorSchema } from "@/lib/blueshell/types.gen.ts"

// Vite note: public env vars must be prefixed with VITE_*
function resolveBaseURL(): string {
  const fromEnv =
    (import.meta.env.VITE_APP_URL as string | undefined) ||
    (import.meta.env.APP_URL as string | undefined) // keep for backward compat if you already set this
  if (fromEnv) return fromEnv
  // Reasonable dev fallback; avoid https on localhost unless you know it's configured
  if (typeof window !== "undefined") return `${window.location.origin}/api`
  return "https://localhost/api"
}

type ApiErrorWithMaybeErrors = ApiErrorSchema & { errors?: unknown }
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
    headers: {
      "Content-Type": "application/json",
    },
  })

  // Keep auth in sync per request (adds OR removes header)
  axiosInstance.interceptors.request.use((cfg) => {
    const token = store.getters.getLogin?.token
    const headers = cfg.headers ?? new AxiosHeaders()

    if (token) {
      if (headers instanceof AxiosHeaders) headers.set("Authorization", `Bearer ${token}`)
      else (headers as any)["Authorization"] = `Bearer ${token}`
    } else {
      if (headers instanceof AxiosHeaders) headers.delete("Authorization")
      else delete (headers as any)["Authorization"]
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
        const errs = (data as any).errors
        if (errs && !Array.isArray(errs)) {
          error.response!.data = { ...(data as any), errors: [errs] } as any
        }
      }
      return Promise.reject(error)
    },
  )

  const {baseURL, ...config} = defaultConfig;

  // Return config to the generated client
  return {
    ...config,
    axios: axiosInstance,
    throwOnError: true,
  } as Config
}
