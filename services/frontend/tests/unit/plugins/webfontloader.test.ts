import {describe, expect, it, vi} from "vitest"

const load = vi.fn()

vi.mock("webfontloader", () => ({
  default: {load},
}))

import {loadFonts} from "@/plugins/webfontloader"

describe("webfontloader plugin", () => {
  it("loads configured fonts", async () => {
    await loadFonts()
    expect(load).toHaveBeenCalledWith({
      google: {
        families: ["Roboto:100,300,400,500,700,900&display=swap"],
      },
    })
  })
})
