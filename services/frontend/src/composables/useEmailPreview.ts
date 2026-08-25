import {ref} from "vue"

/** An email rendered for inspection, as the preview endpoints return it. */
export interface RenderedEmailPreview {
  subject: string
  html: string
  /** Set when the rendered links are inert, so the dialog can say the preview is safe. */
  linkPlaceholder?: string | null
  recipientEmail?: string | null
  recipientName?: string | null
}

/**
 * Holds the state behind an email preview: whether the dialog is open, whether a render is
 * in flight, and the last email returned.
 *
 * The caller supplies the fetch, so this stays free of any one flow's endpoint or inputs —
 * a bulk dialog previewing a reminder and a row previewing an activation use the same
 * composable and differ only in the closure they hand it.
 */
export function useEmailPreview() {
  const open = ref(false)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const preview = ref<RenderedEmailPreview | null>(null)

  /**
   * Open the dialog and render. It opens before the fetch resolves so the operator sees
   * progress rather than a delay, and the previous email is cleared first so a stale one
   * never sits under a fresh error.
   */
  async function show(fetcher: () => Promise<RenderedEmailPreview | null>) {
    open.value = true
    loading.value = true
    error.value = null
    preview.value = null
    try {
      const rendered = await fetcher()
      if (rendered == null) {
        error.value = "The preview could not be rendered."
        return
      }
      preview.value = rendered
    } catch {
      error.value = "The preview could not be rendered."
    } finally {
      loading.value = false
    }
  }

  function reset() {
    open.value = false
    loading.value = false
    error.value = null
    preview.value = null
  }

  return {open, loading, error, preview, show, reset}
}
