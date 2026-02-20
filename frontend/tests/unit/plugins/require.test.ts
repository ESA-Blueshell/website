import {describe, expect, it} from "vitest"
import {$require} from "@/plugins/require"

describe("require plugin", () => {
  it("returns asset url for known assets", () => {
    expect($require("@/assets/topbarlogo.png")).not.toBe("")
  })

  it("returns empty string for unknown assets", () => {
    expect($require("@/assets/does-not-exist.png")).toBe("")
    expect($require(undefined)).toBe("")
  })
})
