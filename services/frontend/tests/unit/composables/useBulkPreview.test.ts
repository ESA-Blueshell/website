import {describe, expect, it} from "vitest"
import {useBulkPreview} from "@/composables/useBulkPreview"
import type {BulkRow} from "@/composables/useBulkPreview"

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

  it("setRows stores serverToday when supplied", () => {
    const p = useBulkPreview()
    p.setRows([row(1, "INCLUDED")], "2025-07-01")
    expect(p.serverToday.value).toBe("2025-07-01")
  })

  it("loadPreview populates rows via the loader and clears loading", async () => {
    const p = useBulkPreview()
    await p.loadPreview(async () => ({rows: [row(1, "INCLUDED")], serverToday: "2025-01-01"}))
    expect(p.rows.value).toHaveLength(1)
    expect(p.serverToday.value).toBe("2025-01-01")
    expect(p.loading.value).toBe(false)
    expect(p.error.value).toBeNull()
  })

  it("loadPreview surfaces an error and empties rows when the loader throws", async () => {
    const p = useBulkPreview()
    await p.loadPreview(async () => {
      throw new Error("boom")
    })
    expect(p.rows.value).toEqual([])
    expect(p.error.value).toBeTruthy()
    expect(p.loading.value).toBe(false)
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
})
