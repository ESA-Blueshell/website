import { ref } from "vue"
import {
  fetchDrift,
  linkUserToExternal,
  removeExternalMember,
} from "../adapters/cohorts"
import type { DriftReport, ExternalUserConflict, TargetSystem } from "../types"

export function useCohortDrift(subjectId: number, system: TargetSystem) {
  const report = ref<DriftReport | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const removing = ref<string | null>(null) // externalUserId being removed
  const linking = ref(false)

  async function load() {
    loading.value = true
    error.value = null
    try {
      report.value = await fetchDrift(subjectId, system)
    } catch (e: unknown) {
      error.value = (e as Error)?.message ?? "Failed to load drift"
    } finally {
      loading.value = false
    }
  }

  async function remove(cohortId: number, externalUserId: string): Promise<void> {
    removing.value = externalUserId
    error.value = null
    try {
      await removeExternalMember(subjectId, cohortId, externalUserId)
      await load()
    } catch (e: unknown) {
      error.value = (e as Error)?.message ?? "Failed to enqueue removal"
    } finally {
      removing.value = null
    }
  }

  async function link(
    userId: number,
    externalUserId: string,
  ): Promise<{ type: "ok" } | { type: "conflict"; conflict: ExternalUserConflict }> {
    linking.value = true
    error.value = null
    try {
      const result = await linkUserToExternal(subjectId, userId, system, externalUserId)
      if (result.type === "ok") {
        await load()
      }
      return result
    } catch (e: unknown) {
      error.value = (e as Error)?.message ?? "Failed to link user"
      throw e
    } finally {
      linking.value = false
    }
  }

  return { report, loading, error, removing, linking, load, remove, link }
}
