import {describe, expect, it} from "vitest"
import {attemptsLabel} from "@/utils/jobAttempts"

describe("attemptsLabel", () => {
  it("shows the exact server attempt count without offsetting", () => {
    expect(attemptsLabel(0)).toBe("0 attempts")
    expect(attemptsLabel(1)).toBe("1 attempt")
    expect(attemptsLabel(4)).toBe("4 attempts")
    expect(attemptsLabel(5)).toBe("5 attempts")
  })

  it("treats undefined as zero", () => {
    expect(attemptsLabel(undefined)).toBe("0 attempts")
  })

  it("increments monotonically across a retry sequence", () => {
    // A job that fails four times and is then manually retried twice should
    // report 4 -> 5 -> 6, not 5 -> 1 -> 4 -> ... (the prior bug).
    const sequence = [0, 1, 2, 3, 4, 5, 6].map(attemptsLabel)
    expect(sequence).toEqual([
      "0 attempts", "1 attempt", "2 attempts", "3 attempts",
      "4 attempts", "5 attempts", "6 attempts",
    ])
  })
})
