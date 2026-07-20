import {computed, ref} from "vue"
import type {BulkActionCounts, BulkPreviewRow} from "@/services/api/blueshell/types.gen"

/**
 * Action-agnostic bulk-preview composable. Holds the preview rows (populated either by a
 * local FE computation or a server call — the composable does not care which), the
 * per-row re-include overrides, the derived counts, the computed included-user set
 * (INCLUDED ∪ re-included WARNING rows), and a submit() runner. It NEVER switches on
 * action type; the per-action differences are the loadPreview / onSubmit closures each
 * dialog supplies. See docs/proposals/bulk-actions/REDESIGN.md §5.1.
 */
export type BulkRow = BulkPreviewRow

export function useBulkPreview() {
  const rows = ref<BulkRow[]>([])

  // Per-row re-include overrides (for WARNING rows the operator can opt back in).
  const reincludeOverrides = ref<Record<number, boolean>>({})

  const submitting = ref(false)

  // Derived counts — the single source of truth for the summary bar.
  // Computed from rows and reincludeOverrides to include re-included WARNING rows.
  const counts = computed<BulkActionCounts>(() => {
    const included = rows.value.filter((r) => {
      if (r.disposition === "INCLUDED") return true
      if (r.disposition === "WARNING" && reincludeOverrides.value[r.userId]) return true
      return false
    }).length

    return {
      selected: rows.value.length,
      willApply: included,
      skipped: rows.value.filter((r) => r.disposition === "SKIPPED").length,
      excluded: rows.value.filter((r) => r.disposition === "EXCLUDED").length,
      warned: rows.value.filter((r) => r.disposition === "WARNING").length,
    }
  })

  const includedUserIds = computed<number[]>(() =>
    rows.value
      .filter((row) => {
        if (row.disposition === "INCLUDED") return true
        if (row.disposition === "WARNING" && reincludeOverrides.value[row.userId]) return true
        return false
      })
      .map((row) => row.userId),
  )

  /** Replace the current rows and reset per-row override state. */
  function setRows(next: BulkRow[]) {
    rows.value = next
    const overrides: Record<number, boolean> = {}
    for (const row of next) overrides[row.userId] = false
    reincludeOverrides.value = overrides
  }

  /**
   * Run a submit action. `fn` performs the API call and resolves to whether it succeeded.
   */
  async function submit(fn: () => Promise<boolean>): Promise<boolean> {
    submitting.value = true
    try {
      return await fn()
    } catch {
      return false
    } finally {
      submitting.value = false
    }
  }

  function reset() {
    rows.value = []
    reincludeOverrides.value = {}
  }

  return {
    rows,
    reincludeOverrides,
    submitting,
    counts,
    includedUserIds,
    setRows,
    submit,
    reset,
  }
}
