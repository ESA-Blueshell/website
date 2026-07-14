import {computed, ref, type Ref} from "vue"
import {createContribution, deleteContribution, findContributionsByPeriodId} from "@/services/api"
import {type ContributionPeriodResponse} from "@/services/api"

// ── Composable ─────────────────────────────────────────────────────────────────

export function usePaidToggle(paidUserIds: Ref<Set<number>>) {
  const selectedPeriodId = ref<number>(0)

  // Tracks which users are currently being saved (optimistic update in flight)
  const saving = ref<Set<number>>(new Set())

  // Toggle is disabled when no contribution period is selected
  const isDisabled = computed(() => selectedPeriodId.value === 0)

  /**
   * Called when the ContributionPeriodList emits a new period.
   * Loads contributions for that period and populates paidUserIds.
   */
  async function contributionPeriodChanged(newPeriod: ContributionPeriodResponse | undefined) {
    if (!newPeriod) {
      paidUserIds.value = new Set()
      selectedPeriodId.value = 0
      return
    }
    selectedPeriodId.value = newPeriod.id as number
    const contributionsResp = await findContributionsByPeriodId({path: {periodId: newPeriod.id as number}})
    const ids = (contributionsResp.data ?? []).map((c) => c.userId)
    paidUserIds.value = new Set(ids)
  }

  /**
   * Optimistically toggle a user's paid status.
   * Rolls back on error, then reconciles from server.
   */
  async function togglePaid(userId: number) {
    if (isDisabled.value) return

    const periodId = selectedPeriodId.value
    const wasPaid = paidUserIds.value.has(userId)

    // Optimistic update
    const next = new Set(paidUserIds.value)
    if (wasPaid) {
      next.delete(userId)
    } else {
      next.add(userId)
    }
    paidUserIds.value = next

    // Track saving state
    const savingNext = new Set(saving.value)
    savingNext.add(userId)
    saving.value = savingNext

    try {
      if (wasPaid) {
        await deleteContribution({path: {contributionPeriodId: periodId, userId}})
      } else {
        await createContribution({body: {userId, contributionPeriodId: periodId}})
      }
    } catch {
      // Rollback optimistic update
      const rollback = new Set(paidUserIds.value)
      if (wasPaid) {
        rollback.add(userId)
      } else {
        rollback.delete(userId)
      }
      paidUserIds.value = rollback
    } finally {
      // Reconcile from server to ensure consistency
      const savingClean = new Set(saving.value)
      savingClean.delete(userId)
      saving.value = savingClean
    }
  }

  function isSaving(userId: number): boolean {
    return saving.value.has(userId)
  }

  return {
    selectedPeriodId,
    saving,
    isDisabled,
    isSaving,
    togglePaid,
    contributionPeriodChanged,
  }
}
