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

/** What Spring answers for a rule that refused in a sentence rather than per field. */
function apiRefusal(status: number, detail: string, extra: Record<string, unknown> = {}) {
  return {
    response: {status, data: {type: "about:blank", status, detail, ...extra}},
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

  it("surfaces a login-action snackbar on 401 without auto-logout or auto-redirect", () => {
    $handleNetworkError(axiosStatusError(401))
    // No auto-logout, no auto-router push: the user might genuinely
    // still be signed in (Vault's OIDC popup chain hits 401 transiently).
    // We just show a snackbar with a Login action and let the user act.
    expect(mockCommit).not.toHaveBeenCalledWith("logout")
    expect(mockPush).not.toHaveBeenCalled()
    expect(mockCommit).toHaveBeenCalledWith(
      "setStatusSnackbarMessage",
      expect.stringContaining("not logged in"),
    )
    expect(mockCommit).toHaveBeenCalledWith("setStatusSnackbarAction", {
      label: "Login",
      to: `/login?redirect=${encodeURIComponent("/events")}`,
    })
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

  // A canned message keyed on the status renamed every deliberate refusal: a taken
  // username became "somebody else changed the same thing... reload the page", which
  // is the one action that loses a signup.
  it("says what the api said when a refusal names no field", () => {
    $handleNetworkError(apiRefusal(409, "That username is already in use"))
    expect(mockCommit).toHaveBeenLastCalledWith(
      "setStatusSnackbarMessage",
      "That username is already in use",
    )
  })

  it("says what the api said when a step is refused outright", () => {
    $handleNetworkError(apiRefusal(403, "This signup did not apply for membership"))
    expect(mockCommit).toHaveBeenLastCalledWith(
      "setStatusSnackbarMessage",
      "This signup did not apply for membership",
    )
  })

  it("leaves a refusal the form already attached to its fields alone", () => {
    $handleNetworkError(
      apiRefusal(400, "Validation failed for request.", {
        errors: [{field: "username", message: "Username is taken."}],
      }),
    )
    expect(mockCommit).toHaveBeenLastCalledWith(
      "setStatusSnackbarMessage",
      expect.stringContaining("bad request"),
    )
  })

  it("keeps the login action on a 401 rather than repeating the api's sentence", () => {
    $handleNetworkError(apiRefusal(401, "Invalid username or password."))
    expect(mockCommit).toHaveBeenCalledWith(
      "setStatusSnackbarMessage",
      expect.stringContaining("not logged in"),
    )
    expect(mockCommit).toHaveBeenCalledWith("setStatusSnackbarAction", expect.anything())
  })

  it("says how long to wait when the api is rate limiting", () => {
    $handleNetworkError({
      response: {
        status: 429,
        headers: {"retry-after": "45"},
        data: {status: 429, detail: "Too many requests. Please try again later."},
      },
    })
    expect(mockCommit).toHaveBeenLastCalledWith(
      "setStatusSnackbarMessage",
      expect.stringContaining("45 seconds"),
    )
  })

  it("still says something useful when a rate limit names no wait", () => {
    $handleNetworkError(apiRefusal(429, "Too many requests. Please try again later."))
    expect(mockCommit).toHaveBeenLastCalledWith(
      "setStatusSnackbarMessage",
      expect.stringContaining("Too many requests"),
    )
  })

  it("keeps its own words where the api's are wire vocabulary", () => {
    $handleNetworkError(apiRefusal(404, "Membership not found with id: 42"))
    expect(mockCommit).toHaveBeenLastCalledWith(
      "setStatusSnackbarMessage",
      expect.stringContaining("404"),
    )
  })

  it("keeps the canned message for a server fault, whose detail is not for reading", () => {
    $handleNetworkError(apiRefusal(500, "NullPointerException at line 42"))
    expect(mockCommit).toHaveBeenLastCalledWith(
      "setStatusSnackbarMessage",
      expect.stringContaining("error code 500"),
    )
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
