/**
 * The redaction rules, checked without a browser.
 *
 * These decide what a job's payload is allowed to put on screen, so the case that matters is the
 * one that must *not* appear: until this file existed the only thing proving a token stays hidden
 * was a page nobody mounted in a unit test.
 */
import {describe, expect, it} from "vitest"
import {
  formatPayloadValue,
  humanizeFieldName,
  isSensitiveKey,
  isUninterestingValue,
  payloadChips,
} from "@/domains/jobs/payload"

describe("job payload redaction", () => {
  it("keeps every secret-shaped key off the row", () => {
    const chips = payloadChips({
      apiToken: "abc123",
      refreshToken: "def456",
      clientSecret: "s3cret",
      password: "hunter2",
      apiKey: "ak_live_1",
      key: "opaque",
      jobName: "nightly sync",
    })

    expect(chips.map(chip => chip.key)).toEqual(["jobName"])
    expect(JSON.stringify(chips)).not.toContain("abc123")
    expect(JSON.stringify(chips)).not.toContain("hunter2")
  })

  it("redacts by shape, so casing cannot smuggle a secret past", () => {
    expect(isSensitiveKey("TOKEN")).toBe(true)
    expect(isSensitiveKey("Discord_Access_Token")).toBe(true)
    expect(isSensitiveKey("APIKEY")).toBe(true)
    expect(isSensitiveKey("KEY")).toBe(true)
    expect(isSensitiveKey("keyword")).toBe(false)
    expect(isSensitiveKey("userId")).toBe(false)
  })

  it("drops the ids the row already shows as a named related entity", () => {
    const chips = payloadChips({
      userId: 7,
      EventId: 3,
      eventSignUpId: 9,
      periodId: 1,
      contributionPeriodId: 2,
      cohortId: 4,
      unused: "x",
      reason: "manual",
    })

    expect(chips.map(chip => chip.key)).toEqual(["reason"])
  })

  it("drops values with nothing in them to read", () => {
    expect(isUninterestingValue(null)).toBe(true)
    expect(isUninterestingValue(undefined)).toBe(true)
    expect(isUninterestingValue("   ")).toBe(true)
    expect(isUninterestingValue({})).toBe(true)
    expect(isUninterestingValue(0)).toBe(false)
    expect(isUninterestingValue(false)).toBe(false)
    expect(payloadChips({empty: {}, blank: "", zero: 0}).map(chip => chip.key)).toEqual(["zero"])
  })

  it("answers with nothing for a payload that is not a map", () => {
    expect(payloadChips(null)).toEqual([])
    expect(payloadChips(undefined)).toEqual([])
    expect(payloadChips("just a string")).toEqual([])
  })

  it("says a field name out loud", () => {
    expect(humanizeFieldName("contributionPeriodId")).toBe("Contribution Period Id")
    expect(humanizeFieldName("job_type")).toBe("Job Type")
    expect(humanizeFieldName("target.system")).toBe("Target System")
  })

  it("renders a value as something a chip can hold", () => {
    expect(formatPayloadValue(null)).toBe("—")
    expect(formatPayloadValue("plain")).toBe("plain")
    expect(formatPayloadValue(42)).toBe("42")
    expect(formatPayloadValue(true)).toBe("true")
    expect(formatPayloadValue({a: 1})).toBe('{"a":1}')
  })

  it("renders a value JSON cannot hold rather than throwing", () => {
    const cyclic: Record<string, unknown> = {name: "loop"}
    cyclic.self = cyclic

    expect(formatPayloadValue(cyclic)).toBe("[object Object]")
  })
})
