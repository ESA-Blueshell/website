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

  it("redirects to login for 401", () => {
    $handleNetworkError(axiosStatusError(401))
    expect(mockPush).toHaveBeenCalledWith({
      path: "/login",
      query: {redirect: "/events"},
    })
    expect(mockCommit).toHaveBeenCalledWith("setStatusSnackbarMessage", expect.stringContaining("not logged in"))
  })

  it("redirects forbidden users for 403", () => {
    $handleNetworkError(axiosStatusError(403))
    expect(mockPush).toHaveBeenCalledWith({path: "/account"})
  })

  it("keeps users on page for 404 with message", () => {
    $handleNetworkError(axiosStatusError(404))
    expect(mockPush).not.toHaveBeenCalled()
    expect(mockCommit).toHaveBeenCalledWith("setStatusSnackbarMessage", expect.stringContaining("404"))
  })

  it("handles no-response request errors", () => {
    $handleNetworkError({request: {}})
    expect(mockCommit).toHaveBeenCalledWith(
      "setStatusSnackbarMessage",
      expect.stringContaining("unknown error"),
    )
  })
})
