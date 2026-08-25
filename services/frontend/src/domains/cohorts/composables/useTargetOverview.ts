import {computed, ref} from "vue"
import {
  fetchTargetDescriptors,
  fetchTargetOptions,
  type ExternalTarget,
  type TargetDescriptor,
  type TargetSystem,
} from "@/domains/cohorts/adapters/cohorts"

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

  /** Targets nothing points at: either finished with, or made by mistake. */
  const unlinkedCount = computed(() => targets.value.filter((t) => t.linkedCohortId == null).length)

  async function load(system: TargetSystem): Promise<void> {
    loading.value = true
    errorMessage.value = null
    try {
      const descriptors = await fetchTargetDescriptors()
      descriptor.value = descriptors.find((item) => item.system === system) ?? null
      targets.value = descriptor.value?.capabilities.includes("CATALOG")
        ? await fetchTargetOptions(system)
        : []
    } catch (err: unknown) {
      errorMessage.value = (err as Error)?.message ?? "Could not load the targets."
    } finally {
      loading.value = false
    }
  }

  return {loading, errorMessage, descriptor, targets, search, matching, folders, unlinkedCount, load}
}
