import { computed, ref } from "vue"
import {
  applyInboundReconcileSelection,
  fetchInboundReconcilePreview,
  type InboundReconcileApplyResponse,
  type InboundReconcilePreview,
} from "@/domains/cohorts/adapters/cohorts"

export function useInboundReconcile() {
  const preview = ref<InboundReconcilePreview | null>(null)
  const selectedExternalUserIds = ref<string[]>([])
  const loading = ref(false)
  const applying = ref(false)
  const errorMessage = ref<string | null>(null)
  const applyResult = ref<InboundReconcileApplyResponse | null>(null)

  const writableRows = computed(() => preview.value?.matched.filter((row) => row.writable) ?? [])
  const canApply = computed(() =>
    Boolean(preview.value?.writerSupported) && selectedExternalUserIds.value.length > 0 && !applying.value,
  )

  async function load(subjectId: number, cohortId: number): Promise<void> {
    loading.value = true
    errorMessage.value = null
    applyResult.value = null
    try {
      preview.value = await fetchInboundReconcilePreview(subjectId, cohortId)
      selectedExternalUserIds.value = writableRows.value.map((row) => row.externalUserId)
    } catch (err: unknown) {
      preview.value = null
      selectedExternalUserIds.value = []
      errorMessage.value = (err as Error)?.message ?? "Could not load inbound reconcile preview."
    } finally {
      loading.value = false
    }
  }

  async function apply(subjectId: number, cohortId: number): Promise<boolean> {
    if (!preview.value || !canApply.value) return false
    applying.value = true
    errorMessage.value = null
    try {
      applyResult.value = await applyInboundReconcileSelection(
        subjectId,
        cohortId,
        preview.value.previewToken,
        selectedExternalUserIds.value,
      )
      return true
    } catch (err: unknown) {
      errorMessage.value = (err as Error)?.message ?? "Could not apply inbound reconcile."
      return false
    } finally {
      applying.value = false
    }
  }

  function reset() {
    preview.value = null
    selectedExternalUserIds.value = []
    errorMessage.value = null
    applyResult.value = null
    loading.value = false
    applying.value = false
  }

  return {
    preview,
    selectedExternalUserIds,
    writableRows,
    loading,
    applying,
    errorMessage,
    applyResult,
    canApply,
    load,
    apply,
    reset,
  }
}
