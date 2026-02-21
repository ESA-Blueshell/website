import {beforeEach, describe, expect, it, vi} from "vitest"

const {
  mockAxiosCreate,
  mockAxiosGet,
  mockStore,
  runtimeState,
} = vi.hoisted(() => ({
  mockAxiosCreate: vi.fn(),
  mockAxiosGet: vi.fn(),
  mockStore: {
    getters: {
      getLogin: null as null | { token?: string },
      getXsrfToken: null as null | string,
    },
    commit: vi.fn(),
  },
  runtimeState: {
    requestInterceptor: null as null | ((cfg: unknown) => Promise<unknown> | unknown),
  },
}))

vi.mock("@/plugins/store.ts", () => ({
  default: mockStore,
}))

vi.mock("axios", () => {
  class MockAxiosHeaders {
    private readonly values = new Map<string, string>()

    set(name: string, value: string) {
      this.values.set(name, value)
    }

    get(name: string): string | undefined {
      return this.values.get(name)
    }

    delete(name: string) {
      this.values.delete(name)
    }
  }

  const instance = {
    defaults: {
      baseURL: "http://localhost:8080",
    },
    get: mockAxiosGet,
    interceptors: {
      request: {
        use: vi.fn((fn: (cfg: unknown) => Promise<unknown> | unknown) => {
          runtimeState.requestInterceptor = fn
          return 0
        }),
      },
      response: {
        use: vi.fn(() => 0),
      },
    },
  }

  mockAxiosCreate.mockReturnValue(instance)

  return {
    default: {
      create: mockAxiosCreate,
    },
    AxiosHeaders: MockAxiosHeaders,
  }
})

import {AxiosHeaders} from "axios"
import {createClientConfig} from "@/services/api/blueshell.runtime"

describe("blueshell runtime csrf behavior", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockAxiosCreate.mockReturnValue({
      defaults: {
        baseURL: "http://localhost:8080",
      },
      get: mockAxiosGet,
      interceptors: {
        request: {
          use: vi.fn((fn: (cfg: unknown) => Promise<unknown> | unknown) => {
            runtimeState.requestInterceptor = fn
            return 0
          }),
        },
        response: {
          use: vi.fn(() => 0),
        },
      },
    })
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/"
    mockStore.getters.getLogin = null
    mockStore.getters.getXsrfToken = null
    runtimeState.requestInterceptor = null
  })

  it("bootstraps csrf token for mutating requests and sends csrf header", async () => {
    mockStore.getters.getLogin = {token: "jwt-token"}
    mockAxiosGet.mockImplementation(async () => {
      document.cookie = "XSRF-TOKEN=csrf-token"
      return {data: {token: "csrf-token"}}
    })

    createClientConfig({} as never)
    const interceptor = runtimeState.requestInterceptor
    expect(interceptor).toBeTypeOf("function")

    const cfg = {
      method: "post",
      headers: new AxiosHeaders(),
    } as Record<string, unknown>

    const updated = await interceptor!(cfg)
    const headers = (updated as { headers: AxiosHeaders }).headers

    expect(mockAxiosGet).toHaveBeenCalledWith("/csrf", {withCredentials: true})
    expect((updated as { withCredentials: boolean }).withCredentials).toBe(true)
    expect(headers.get("Authorization")).toBe("Bearer jwt-token")
    expect(headers.get("X-XSRF-TOKEN")).toBe("csrf-token")
    expect(mockStore.commit).toHaveBeenCalledWith("setXsrfToken", "csrf-token")
  })

  it("does not bootstrap csrf token for safe methods", async () => {
    document.cookie = "XSRF-TOKEN=existing-token"

    createClientConfig({} as never)
    const interceptor = runtimeState.requestInterceptor
    expect(interceptor).toBeTypeOf("function")

    const cfg = {
      method: "get",
      headers: new AxiosHeaders(),
    } as Record<string, unknown>

    const updated = await interceptor!(cfg)
    const headers = (updated as { headers: AxiosHeaders }).headers

    expect(mockAxiosGet).not.toHaveBeenCalled()
    expect((updated as { withCredentials: boolean }).withCredentials).toBe(true)
    expect(headers.get("X-XSRF-TOKEN")).toBeUndefined()
  })

  it("uses token from csrf bootstrap response body when cookie is not readable", async () => {
    mockAxiosGet.mockResolvedValue({data: {token: "body-token"}})

    createClientConfig({} as never)
    const interceptor = runtimeState.requestInterceptor
    expect(interceptor).toBeTypeOf("function")

    const cfg = {
      method: "patch",
      headers: new AxiosHeaders(),
    } as Record<string, unknown>

    const updated = await interceptor!(cfg)
    const headers = (updated as { headers: AxiosHeaders }).headers

    expect(mockAxiosGet).toHaveBeenCalledWith("/csrf", {withCredentials: true})
    expect(headers.get("X-XSRF-TOKEN")).toBe("body-token")
    expect(mockStore.commit).toHaveBeenCalledWith("setXsrfToken", "body-token")
  })

  it("reproduces csrf mismatch when bootstrap body token differs from csrf cookie", async () => {
    mockAxiosGet.mockImplementation(async () => {
      // Mirrors observed dev flow: /csrf returns one token in body and a different cookie value.
      document.cookie = "XSRF-TOKEN=565bd90a-08a7-4de5-965a-c8e94f9eaad1"
      return {data: {token: "CM47rb-bXq-e_fmcGrFD4tQ-n-9abFagWJr2D_H-N9y97I-RPfgOz9uibs6zzcH9LZx3hrELstZsWTeNO6KTNsWYDrncjeug"}}
    })

    createClientConfig({} as never)
    const interceptor = runtimeState.requestInterceptor
    expect(interceptor).toBeTypeOf("function")

    const cfg = {
      method: "post",
      headers: new AxiosHeaders(),
    } as Record<string, unknown>

    const updated = await interceptor!(cfg)
    const headers = (updated as { headers: AxiosHeaders }).headers

    // Expected for this backend flow: use /csrf response token in header.
    // Regression guard: do not use cookie value as header token.
    expect(headers.get("X-XSRF-TOKEN")).toBe(
      "CM47rb-bXq-e_fmcGrFD4tQ-n-9abFagWJr2D_H-N9y97I-RPfgOz9uibs6zzcH9LZx3hrELstZsWTeNO6KTNsWYDrncjeug",
    )
  })

  it("refreshes csrf token for each mutating request", async () => {
    mockAxiosGet.mockResolvedValue({data: {token: "fresh-token"}})

    createClientConfig({} as never)
    const interceptor = runtimeState.requestInterceptor
    expect(interceptor).toBeTypeOf("function")

    const cfg = {
      method: "post",
      headers: new AxiosHeaders(),
    } as Record<string, unknown>

    await interceptor!(cfg)
    await interceptor!(cfg)

    expect(mockAxiosGet).toHaveBeenCalledTimes(2)
  })

  it("falls back to stored csrf token when bootstrap fails", async () => {
    mockStore.getters.getXsrfToken = "stored-token"
    mockAxiosGet.mockRejectedValue(new Error("bootstrap failed"))

    createClientConfig({} as never)
    const interceptor = runtimeState.requestInterceptor
    expect(interceptor).toBeTypeOf("function")

    const cfg = {
      method: "put",
      headers: new AxiosHeaders(),
    } as Record<string, unknown>

    const updated = await interceptor!(cfg)
    const headers = (updated as { headers: AxiosHeaders }).headers

    expect(mockAxiosGet).toHaveBeenCalledWith("/csrf", {withCredentials: true})
    expect(headers.get("X-XSRF-TOKEN")).toBe("stored-token")
  })

  it("removes stale authorization header when user has no login token", async () => {
    createClientConfig({} as never)
    const interceptor = runtimeState.requestInterceptor
    expect(interceptor).toBeTypeOf("function")

    const headers = new AxiosHeaders()
    headers.set("Authorization", "Bearer stale-token")

    const cfg = {
      method: "get",
      headers,
    } as Record<string, unknown>

    const updated = await interceptor!(cfg)
    const updatedHeaders = (updated as { headers: AxiosHeaders }).headers

    expect(updatedHeaders.get("Authorization")).toBeUndefined()
  })
})
