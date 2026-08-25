import { computed, reactive, ref } from "vue"
import {
  createTargetForSubject,
  fetchTargetDescriptors,
  fetchTargetOptions,
  linkExistingTargetForSubject,
  switchCohortTarget,
  type TargetCapability,
  type ExternalTarget,
  type TargetDescriptor,
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
  const loading = ref(false)
  const errorMessage = ref<string | null>(null)
  const conflict = ref(false)
  const descriptors = ref<TargetDescriptor[]>([])
  const descriptor = ref<TargetDescriptor | null>(null)
  const options = ref<ExternalTarget[]>([])

  const form = reactive({
    tab: "existing" as TargetPickerTab,
    externalId: "",
    search: "",
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
    form.search = ""
    form.label = ""
    form.folderHint = ""
    form.deletePrevious = false
    form.reconcileNow = false
  }

  const hasCatalog = computed(() => supports("CATALOG"))
  const canCreate = computed(() => supports("CREATE"))
  const filteredOptions = computed(() => {
    const q = form.search.trim().toLowerCase()
    if (!q) return options.value
    return options.value.filter((target) => matches(target, q))
  })

  /**
   * The folders the catalogue mentions, in order, without repeats. Creating a target
   * usually means putting it beside the ones already there, and the catalogue names each
   * target's folder, so the folders that exist are already known — no extra call, and no
   * folder offered that turns out not to be there.
   *
   * A folder holding no targets cannot appear, which is why the field stays free text: a
   * name that is not on the list is still a name that can be typed.
   */
  const folderOptions = computed(() =>
    [...new Set(options.value.map((target) => target.folderLabel).filter((f): f is string => !!f))].sort(
      (a, b) => a.localeCompare(b),
    ),
  )

  async function load(system: TargetSystem): Promise<void> {
    loading.value = true
    errorMessage.value = null
    try {
      if (descriptors.value.length === 0) descriptors.value = await fetchTargetDescriptors()
      descriptor.value = descriptors.value.find((item) => item.system === system) ?? null
      options.value = hasCatalog.value ? await fetchTargetOptions(system) : []
    } catch (err: unknown) {
      errorMessage.value = (err as Error)?.message ?? "Could not load targets."
    } finally {
      loading.value = false
    }
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

  function supports(capability: TargetCapability): boolean {
    return descriptor.value?.capabilities.includes(capability) ?? false
  }

  function matches(target: ExternalTarget, query: string): boolean {
    return (
      target.externalId.toLowerCase() === query ||
      target.label.toLowerCase().includes(query) ||
      (target.folderLabel ?? "").toLowerCase().includes(query)
    )
  }

  return {
    submitting,
    loading,
    errorMessage,
    conflict,
    descriptor,
    options,
    filteredOptions,
    folderOptions,
    hasCatalog,
    canCreate,
    form,
    reset,
    load,
    submitAdd,
    submitSwitch,
  }
}
