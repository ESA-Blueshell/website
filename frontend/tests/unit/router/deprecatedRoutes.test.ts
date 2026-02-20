import {describe, expect, it} from "vitest"
import router from "@/plugins/router"

describe("Deprecated routes", () => {
  it("does not expose a navigable trackmania route", () => {
    const resolved = router.resolve("/esports/trackmania")

    expect(resolved.name).toBe("NotFound")
  })
})
