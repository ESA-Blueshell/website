import {describe, expect, it} from "vitest"
import {useBulkPreview} from "@/composables/useBulkPreview"
import type {BulkRow} from "@/composables/useBulkPreview"

/**
 * Test suite for the action-agnostic bulk-preview composable.
 * Verifies row management, counts, re-include logic, and submit handling.
 */

function row(userId: number, disposition: BulkRow["disposition"]): BulkRow {
  return {userId, name: `User ${userId}`, disposition}
}

describe("useBulkPreview", () => {
  it("derives counts from the rows", () => {
    const p = useBulkPreview()
    p.setRows([row(1, "INCLUDED"), row(2, "WARNING"), row(3, "EXCLUDED"), row(4, "SKIPPED"), row(5, "INCLUDED")])
    expect(p.counts.value).toEqual({selected: 5, willApply: 2, warned: 1, excluded: 1, skipped: 1})
  })

  it("includedUserIds is INCLUDED rows only until a WARNING row is re-included", () => {
    const p = useBulkPreview()
    p.setRows([row(1, "INCLUDED"), row(2, "WARNING"), row(3, "EXCLUDED")])
    expect(p.includedUserIds.value).toEqual([1])

    // Operator opts the WARNING row back in.
    p.reincludeOverrides.value = {...p.reincludeOverrides.value, 2: true}
    expect(p.includedUserIds.value).toEqual([1, 2])
  })

  it("never includes EXCLUDED or SKIPPED rows even if flagged for re-include", () => {
    const p = useBulkPreview()
    p.setRows([row(3, "EXCLUDED"), row(4, "SKIPPED")])
    p.reincludeOverrides.value = {3: true, 4: true}
    expect(p.includedUserIds.value).toEqual([])
  })

  it("setRows resets per-row overrides to false", () => {
    const p = useBulkPreview()
    p.setRows([row(1, "WARNING")])
    p.reincludeOverrides.value = {1: true}
    p.setRows([row(1, "WARNING"), row(2, "WARNING")])
    expect(p.reincludeOverrides.value).toEqual({1: false, 2: false})
  })

  it("submit toggles submitting and returns the runner's result", async () => {
    const p = useBulkPreview()
    const ok = await p.submit(async () => true)
    expect(ok).toBe(true)
    expect(p.submitting.value).toBe(false)

    const failed = await p.submit(async () => {
      throw new Error("nope")
    })
    expect(failed).toBe(false)
  })

  it("reset clears rows and overrides", () => {
    const p = useBulkPreview()
    p.setRows([row(1, "INCLUDED"), row(2, "WARNING")])
    p.reincludeOverrides.value = {2: true}
    p.reset()
    expect(p.rows.value).toEqual([])
    expect(p.reincludeOverrides.value).toEqual({})
  })
})
