import {beforeEach, describe, expect, it, vi} from "vitest"

const {mockPush, mockCommit} = vi.hoisted(() => ({
  mockPush: vi.fn(),
  mockCommit: vi.fn(),
}))

vi.mock("@/plugins/router", () => ({
  default: {
    currentRoute: {
      value: {
        fullPath: "/events",
        query: {},
      },
    },
    push: mockPush,
  },
}))

vi.mock("@/plugins/store", () => ({
  default: {
    commit: mockCommit,
  },
}))

import {$handleNetworkError} from "@/plugins/handleNetworkError"

function axiosStatusError(status: number) {
  return {
    response: {status},
  }
}

describe("handleNetworkError plugin", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("handles unknown non-axios errors", () => {
    $handleNetworkError(new Error("unexpected"))
    expect(mockCommit).toHaveBeenCalledWith("setStatusSnackbarMessage", expect.stringContaining("unknown error"))
  })

  it("surfaces a login-link snackbar on 401 without auto-logout or auto-redirect", () => {
    $handleNetworkError(axiosStatusError(401))
    // No auto-logout, no auto-router push: the user might genuinely
    // still be signed in (Vault's OIDC popup chain hits 401 transiently).
    // We just show a snackbar with a Login link and let the user act.
    expect(mockCommit).not.toHaveBeenCalledWith("logout")
    expect(mockPush).not.toHaveBeenCalled()
    expect(mockCommit).toHaveBeenCalledWith(
      "setStatusSnackbarMessage",
      expect.stringContaining("not logged in"),
    )
    expect(mockCommit).toHaveBeenCalledWith(
      "setStatusSnackbarMessage",
      expect.stringContaining(`/login?redirect=${encodeURIComponent("/events")}`),
    )
  })

  it("surfaces a snackbar on 403 without auto-redirecting to /account", () => {
    $handleNetworkError(axiosStatusError(403))
    expect(mockPush).not.toHaveBeenCalled()
    expect(mockCommit).toHaveBeenCalledWith(
      "setStatusSnackbarMessage",
      expect.stringContaining("authority"),
    )
  })

  it("keeps users on page for 404 with message", () => {
    $handleNetworkError(axiosStatusError(404))
    expect(mockPush).not.toHaveBeenCalled()
    expect(mockCommit).toHaveBeenCalledWith("setStatusSnackbarMessage", expect.stringContaining("404"))
  })

  it("shows tailored messages for additional known status codes", () => {
    $handleNetworkError(axiosStatusError(400))
    expect(mockCommit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", expect.stringContaining("bad request"))

    $handleNetworkError(axiosStatusError(408))
    expect(mockCommit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", expect.stringContaining("request timeout"))

    $handleNetworkError(axiosStatusError(409))
    expect(mockCommit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", expect.stringContaining("Woopsie daysies"))

    $handleNetworkError(axiosStatusError(413))
    expect(mockCommit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", expect.stringContaining("file is too large"))

    $handleNetworkError(axiosStatusError(500))
    expect(mockCommit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", expect.stringContaining("error code 500"))

    $handleNetworkError(axiosStatusError(502))
    expect(mockCommit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", expect.stringContaining("error code 502"))
  })

  it("falls back to generic status message for unknown status codes", () => {
    $handleNetworkError(axiosStatusError(418))
    expect(mockCommit).toHaveBeenCalledWith("setStatusSnackbarMessage", expect.stringContaining("error code 418"))
  })

  it("handles no-response request errors", () => {
    $handleNetworkError({request: {}})
    expect(mockCommit).toHaveBeenCalledWith(
      "setStatusSnackbarMessage",
      expect.stringContaining("unknown error"),
    )
  })
})
