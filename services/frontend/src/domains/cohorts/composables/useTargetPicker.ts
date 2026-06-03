import { reactive, ref } from "vue"
import {
  createTargetForSubject,
  linkExistingTargetForSubject,
  switchCohortTarget,
  type TargetSystem,
} from "@/domains/cohorts/adapters/cohorts"

export type TargetPickerTab = "existing" | "create"

/**
 * Drives the external target picker — linking an existing target,
 * creating a fresh one, or switching a mapping. Reusable and stateful
 * (per ADR-006), so it lives here rather than inline in the modal. All
 * SDK access goes through the cohorts adapter.
 */
export function useTargetPicker() {
  const submitting = ref(false)
  const errorMessage = ref<string | null>(null)
  const conflict = ref(false)

  const form = reactive({
    tab: "existing" as TargetPickerTab,
    externalId: "",
    label: "",
    folderHint: "",
    deletePrevious: false,
    reconcileNow: false,
  })

  function reset() {
    submitting.value = false
    errorMessage.value = null
    conflict.value = false
    form.tab = "existing"
    form.externalId = ""
    form.label = ""
    form.folderHint = ""
    form.deletePrevious = false
    form.reconcileNow = false
  }

  /** Link or create, depending on the active tab. Returns true on success. */
  async function submitAdd(subjectId: number, system: TargetSystem): Promise<boolean> {
    submitting.value = true
    errorMessage.value = null
    conflict.value = false
    try {
      const result =
        form.tab === "create"
          ? await createTargetForSubject(subjectId, system, form.label.trim(), form.folderHint.trim() || null)
          : await linkExistingTargetForSubject(subjectId, system, form.externalId.trim())
      if (result.type === "conflict") {
        conflict.value = true
        return false
      }
      return true
    } catch (err: unknown) {
      errorMessage.value = (err as Error)?.message ?? "Could not save the target."
      return false
    } finally {
      submitting.value = false
    }
  }

  /** Repoint an existing cohort at a different target. Returns true on success. */
  async function submitSwitch(subjectId: number, cohortId: number): Promise<boolean> {
    submitting.value = true
    errorMessage.value = null
    try {
      await switchCohortTarget(
        subjectId,
        cohortId,
        form.externalId.trim(),
        form.deletePrevious,
        form.reconcileNow,
      )
      return true
    } catch (err: unknown) {
      errorMessage.value = (err as Error)?.message ?? "Could not switch the target."
      return false
    } finally {
      submitting.value = false
    }
  }

  return { submitting, errorMessage, conflict, form, reset, submitAdd, submitSwitch }
}
