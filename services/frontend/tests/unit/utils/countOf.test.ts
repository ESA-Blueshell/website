import {describe, expect, it} from "vitest"
import {countOf} from "@/utils/countOf"

describe("countOf", () => {
  it("names one of a thing in the singular and any other count in the plural", () => {
    expect(countOf(1, "person", "people")).toBe("1 person")
    expect(countOf(0, "person", "people")).toBe("0 people")
    expect(countOf(3, "team", "teams")).toBe("3 teams")
  })
})
