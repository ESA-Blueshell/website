import {beforeEach, describe, expect, it, vi} from "vitest"

const {mockPush} = vi.hoisted(() => ({
  mockPush: vi.fn(),
}))

vi.mock("@/plugins/router", () => ({
  default: {
    push: mockPush,
  },
}))

import {$goto} from "@/plugins/goto"

describe("goto plugin", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("opens external urls in a new tab", () => {
    const focus = vi.fn()
    const open = vi.spyOn(window, "open").mockReturnValue({focus} as never)

    $goto("https://example.com")

    expect(open).toHaveBeenCalledWith("https://example.com", "_blank")
    expect(focus).toHaveBeenCalled()
  })

  it("pushes internal routes through router", () => {
    const scrollTo = vi.spyOn(window, "scrollTo").mockImplementation(() => undefined)

    $goto("/events")

    expect(scrollTo).toHaveBeenCalled()
    expect(mockPush).toHaveBeenCalledWith("/events")
  })
})
