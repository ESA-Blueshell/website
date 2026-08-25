import {computed, ref} from "vue"
import {
  fetchTargetDescriptors,
  fetchTargetFolders,
  fetchTargetOptions,
  moveTargetToFolder,
  moveTargetsToFolder,
  type BulkTargetMoveResult,
  type ExternalTarget,
  type TargetDescriptor,
  type TargetSystem,
} from "@/domains/cohorts/adapters/cohorts"
import type {BulkRejection} from "@/utils/bulkRejection"

/** A folder and the targets filed under it. */
export interface TargetFolder {
  /** Null for targets the system filed nowhere. */
  label: string | null
  targets: ExternalTarget[]
  memberCount: number | null
  linkedCount: number
}

/**
 * Everything a target system holds, grouped by folder.
 *
 * The catalogue already returns each target's folder, name, member count and the cohort
 * linked to it, so this needs nothing the picker did not already fetch — it just shows all
 * of it at once instead of only what you are choosing between.
 */
export function useTargetOverview() {
  const loading = ref(false)
  const errorMessage = ref<string | null>(null)
  const descriptor = ref<TargetDescriptor | null>(null)
  const targets = ref<ExternalTarget[]>([])
  const search = ref("")
  /**
   * Read from the system rather than inferred from the targets. A folder holding nothing is
   * invisible to the catalogue, and an empty folder is exactly where a target is headed.
   */
  const folderNames = ref<string[]>([])
  const moving = ref<string | null>(null)

  /** The external ids ticked for a move. A Set, because the page asks it per row. */
  const selection = ref<Set<string>>(new Set())
  const movingSelection = ref(false)
  /** The api refused the whole selection; nothing was sent. */
  const rejection = ref<BulkRejection | null>(null)
  /** The selection was valid but the system would not move these. */
  const failedMoves = ref<BulkTargetMoveResult["failed"]>([])

  const selectedIds = computed(() => [...selection.value])
  const selectedCount = computed(() => selection.value.size)

  const canMove = computed(() => descriptor.value?.capabilities.includes("MOVE") ?? false)

  const matching = computed(() => {
    const query = search.value.trim().toLowerCase()
    if (!query) return targets.value
    return targets.value.filter((target) =>
      [target.label, target.folderLabel, target.externalId]
        .some((field) => (field ?? "").toLowerCase().includes(query)),
    )
  })

  /** Folders in name order, with the unfiled ones last because they are the exception. */
  const folders = computed<TargetFolder[]>(() => {
    const byFolder = new Map<string | null, ExternalTarget[]>()
    for (const target of matching.value) {
      const key = target.folderLabel ?? null
      byFolder.set(key, [...(byFolder.get(key) ?? []), target])
    }
    return [...byFolder.entries()]
      .sort(([a], [b]) => {
        if (a === null) return 1
        if (b === null) return -1
        return a.localeCompare(b)
      })
      .map(([label, group]) => ({
        label,
        targets: [...group].sort((a, b) => a.label.localeCompare(b.label)),
        // Null rather than zero when nothing reported a count, so "unknown" is not "empty".
        memberCount: group.every((t) => t.memberCount == null)
          ? null
          : group.reduce((sum, t) => sum + (t.memberCount ?? 0), 0),
        linkedCount: group.filter((t) => t.linkedCohortId != null).length,
      }))
  })

  /** Whether every target the search currently shows is ticked. */
  const allMatchingSelected = computed(() =>
    matching.value.length > 0 && matching.value.every((target) => selection.value.has(target.externalId)))

  function isSelected(externalId: string): boolean {
    return selection.value.has(externalId)
  }

  function toggleSelection(externalId: string): void {
    // Replaced rather than mutated: a Set mutated in place is the same object, and the
    // computeds reading it would not re-run.
    const next = new Set(selection.value)
    if (!next.delete(externalId)) next.add(externalId)
    selection.value = next
  }

  /** Ticks every target the search shows, or clears them if they are already all ticked. */
  function toggleAllMatching(): void {
    selection.value = allMatchingSelected.value
      ? new Set()
      : new Set(matching.value.map((target) => target.externalId))
  }

  function clearSelection(): void {
    selection.value = new Set()
    rejection.value = null
    failedMoves.value = []
  }

  /** Targets nothing points at: either finished with, or made by mistake. */
  const unlinkedCount = computed(() => targets.value.filter((t) => t.linkedCohortId == null).length)

  async function load(system: TargetSystem): Promise<void> {
    loading.value = true
    errorMessage.value = null
    try {
      const descriptors = await fetchTargetDescriptors()
      descriptor.value = descriptors.find((item) => item.system === system) ?? null
      const hasCatalog = descriptor.value?.capabilities.includes("CATALOG") ?? false
      targets.value = hasCatalog ? await fetchTargetOptions(system) : []
      folderNames.value = descriptor.value?.capabilities.includes("MOVE")
        ? await fetchTargetFolders(system)
        : []
    } catch (err: unknown) {
      errorMessage.value = (err as Error)?.message ?? "Could not load the targets."
    } finally {
      loading.value = false
    }
  }

  /**
   * File one target elsewhere. The row is updated from what the api answers rather than from
   * what was asked for, so the page shows where the target actually ended up.
   */
  async function move(system: TargetSystem, target: ExternalTarget, folder: string): Promise<boolean> {
    moving.value = target.externalId
    errorMessage.value = null
    try {
      const moved = await moveTargetToFolder(system, target.externalId, folder)
      targets.value = targets.value.map((t) => (t.externalId === moved.externalId ? moved : t))
      return true
    } catch (err: unknown) {
      errorMessage.value = (err as Error)?.message ?? "Could not move the target."
      return false
    } finally {
      moving.value = null
    }
  }

  /**
   * File everything ticked under one folder.
   *
   * A refused selection is kept as such rather than reported as an error: nothing was sent, so
   * the ticks stay where they were and the reasons name what is wrong with them. A selection
   * that was accepted clears, minus anything the system would not move — those keep their ticks
   * so a retry does not need them found again.
   */
  async function moveSelected(system: TargetSystem, folder: string): Promise<boolean> {
    if (selection.value.size === 0) return false
    movingSelection.value = true
    errorMessage.value = null
    rejection.value = null
    failedMoves.value = []
    try {
      const outcome = await moveTargetsToFolder(system, selectedIds.value, folder)
      if (outcome.status === "refused") {
        rejection.value = outcome.rejection
        return false
      }
      const {moved, failed} = outcome.result
      const byId = new Map(moved.map((target) => [target.externalId, target]))
      targets.value = targets.value.map((target) => byId.get(target.externalId) ?? target)
      failedMoves.value = failed
      selection.value = new Set(failed.map((row) => row.externalId))
      return failed.length === 0
    } catch (err: unknown) {
      errorMessage.value = (err as Error)?.message ?? "Could not move the targets."
      return false
    } finally {
      movingSelection.value = false
    }
  }

  return {
    loading,
    errorMessage,
    descriptor,
    targets,
    search,
    matching,
    folders,
    folderNames,
    unlinkedCount,
    canMove,
    moving,
    selection,
    selectedIds,
    selectedCount,
    allMatchingSelected,
    movingSelection,
    rejection,
    failedMoves,
    isSelected,
    toggleSelection,
    toggleAllMatching,
    clearSelection,
    load,
    move,
    moveSelected,
  }
}
