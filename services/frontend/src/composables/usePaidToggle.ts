import {computed, ref, type Ref} from "vue"
import {createContribution, deleteContribution, findContributionsByPeriodId} from "@/services/api"
import {type ContributionPeriodResponse} from "@/services/api"

// ── Composable ─────────────────────────────────────────────────────────────────

export function usePaidToggle(paidUserIds: Ref<Set<number>>) {
  const selectedPeriodId = ref<number>(0)
  const selectedPeriod = ref<ContributionPeriodResponse | null>(null)

  // Tracks which users are currently being saved (optimistic update in flight)
  const saving = ref<Set<number>>(new Set())

  /**
   * Whether who paid is actually known. False after a read that failed, where an empty
   * `paidUserIds` would otherwise read as "nobody paid" and be booked against.
   */
  const paidKnown = ref(true)
  const loadFailure = ref<string | null>(null)

  /** Set when the server refused a toggle, so the reverted row is not a mystery. */
  const saveFailure = ref<string | null>(null)

  /**
   * Which read the shown set belongs to. Two picks in quick succession can answer out of
   * order, and a stale answer that stamped `paidKnown` would make the wrong period's set look
   * confirmed rather than merely wrong.
   */
  let generation = 0

  // Nothing is toggled without a period, or against a set that was never read.
  const isDisabled = computed(() => selectedPeriodId.value === 0 || !paidKnown.value)

  /**
   * Called when the ContributionPeriodList emits a new period.
   * Loads contributions for that period and populates paidUserIds.
   */
  async function contributionPeriodChanged(newPeriod: ContributionPeriodResponse | undefined) {
    const mine = ++generation
    loadFailure.value = null
    if (!newPeriod) {
      paidUserIds.value = new Set()
      selectedPeriodId.value = 0
      selectedPeriod.value = null
      paidKnown.value = true
      return
    }
    selectedPeriodId.value = newPeriod.id as number
    selectedPeriod.value = newPeriod
    // Nothing is claimed about the new period until its own answer is in, so the table cannot
    // attribute the previous period's set to it while the read is in flight.
    paidKnown.value = false
    paidUserIds.value = new Set()
    const contributionsResp = await findContributionsByPeriodId({path: {periodId: newPeriod.id as number}})
    if (mine !== generation) return
    if (contributionsResp.error || !contributionsResp.data) {
      loadFailure.value = "Who paid in this period could not be read, so it is not shown. "
        + "Pick the period again to try once more."
      return
    }
    paidKnown.value = true
    paidUserIds.value = new Set(contributionsResp.data.map((c) => c.userId))
  }

  /**
   * Optimistically toggle a user's paid status, and put it back if the server refuses.
   *
   * The client resolves with an `error` instead of throwing, so the refusal has to be read
   * off the result: a `catch` here never runs, and a payment that was never recorded would
   * otherwise stay green and be booked against.
   */
  async function togglePaid(userId: number) {
    if (isDisabled.value) return
    saveFailure.value = null

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

    // Puts the row back the way the server still has it.
    const undo = () => {
      const rollback = new Set(paidUserIds.value)
      if (wasPaid) {
        rollback.add(userId)
      } else {
        rollback.delete(userId)
      }
      paidUserIds.value = rollback
      saveFailure.value = wasPaid
        ? "That payment could not be withdrawn, so it still stands."
        : "That payment could not be recorded, so it is not booked."
    }

    try {
      const resp = wasPaid
        ? await deleteContribution({path: {contributionPeriodId: periodId, userId}})
        : await createContribution({body: {userId, contributionPeriodId: periodId}})

      if (resp.error) undo()
    } catch {
      // The client resolves rather than throws today, so this is the belt to that
      // brace: an interceptor or a future `throwOnError` must not skip the undo.
      undo()
    } finally {
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
    selectedPeriod,
    saving,
    paidKnown,
    loadFailure,
    saveFailure,
    isDisabled,
    isSaving,
    togglePaid,
    contributionPeriodChanged,
  }
}
