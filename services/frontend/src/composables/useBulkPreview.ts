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
  const serverToday = ref<string | null>(null)

  const loading = ref(false)
  const error = ref<string | null>(null)

  // Per-row re-include overrides (for WARNING rows the operator can opt back in).
  const reincludeOverrides = ref<Record<number, boolean>>({})

  const submitting = ref(false)

  // Derived counts — the single source of truth for the summary bar (no reliance on a
  // server-supplied counts object, so FE-computed previews get identical treatment).
  const counts = computed<BulkActionCounts>(() => ({
    selected: rows.value.length,
    willApply: rows.value.filter((r) => r.disposition === "INCLUDED").length,
    skipped: rows.value.filter((r) => r.disposition === "SKIPPED").length,
    excluded: rows.value.filter((r) => r.disposition === "EXCLUDED").length,
    warned: rows.value.filter((r) => r.disposition === "WARNING").length,
  }))

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
  function setRows(next: BulkRow[], today: string | null = null) {
    rows.value = next
    serverToday.value = today
    const overrides: Record<number, boolean> = {}
    for (const row of next) overrides[row.userId] = false
    reincludeOverrides.value = overrides
  }

  /**
   * Populate rows via a loader. The loader returns the rows (and optional serverToday);
   * this handles the loading / error flags uniformly for both FE and server previews.
   */
  async function loadPreview(
    loader: () => Promise<{rows: BulkRow[]; serverToday?: string | null}>,
  ): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const result = await loader()
      setRows(result.rows, result.serverToday ?? null)
    } catch {
      error.value = "An error occurred loading the preview."
      rows.value = []
    } finally {
      loading.value = false
    }
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
    serverToday.value = null
    error.value = null
    reincludeOverrides.value = {}
  }

  return {
    rows,
    serverToday,
    loading,
    error,
    reincludeOverrides,
    submitting,
    counts,
    includedUserIds,
    setRows,
    loadPreview,
    submit,
    reset,
  }
}
