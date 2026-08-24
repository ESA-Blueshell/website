import {ref} from "vue"
import {previewRecoveryEmail, TokenPurpose, type RecoveryEmailPreviewResponse} from "@/services/api"

/**
 * Fetches a recovery email rendered for inspection. The preview issues no token, so
 * opening one is free of consequences and can be repeated.
 */
export function useRecoveryEmailPreview() {
  const open = ref(false)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const preview = ref<RecoveryEmailPreviewResponse | null>(null)

  async function show(userId: number, purpose: TokenPurpose) {
    open.value = true
    loading.value = true
    error.value = null
    preview.value = null
    const {data} = await previewRecoveryEmail({path: {userId}, query: {purpose}})
    loading.value = false
    if (data) {
      preview.value = data
    } else {
      error.value = "The preview could not be rendered."
    }
  }

  return {open, loading, error, preview, show}
}
