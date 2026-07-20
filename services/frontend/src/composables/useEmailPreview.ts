import {ref} from "vue"

/**
 * Holds the email-preview state for a bulk email dialog: the currently-selected preview
 * recipient, the nested-dialog open flag, loading/error, and the last rendered email. The
 * caller supplies a `fetcher` that performs the actual (action-specific) preview API call
 * for a given userId and resolves to the rendered {subject, html}. This composable is
 * action-agnostic; the reminder / incasso dialogs wire in their own endpoint + inputs.
 */
export interface RenderedEmail {
  subject: string
  html: string
}

export function useEmailPreview() {
  const selectedUserId = ref<number | null>(null)
  const dialogOpen = ref(false)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const subject = ref<string | null>(null)
  const html = ref<string | null>(null)

  /**
   * Run a preview: opens the nested dialog, shows a loading state, calls `fetcher` for the
   * currently-selected user, and stores the rendered email (or an error message).
   */
  async function runPreview(fetcher: (userId: number) => Promise<RenderedEmail | null>) {
    const userId = selectedUserId.value
    if (userId == null) return
    dialogOpen.value = true
    loading.value = true
    error.value = null
    subject.value = null
    html.value = null
    try {
      const rendered = await fetcher(userId)
      if (rendered == null) {
        error.value = "Could not render the email preview."
        return
      }
      subject.value = rendered.subject
      html.value = rendered.html
    } catch {
      error.value = "Could not render the email preview."
    } finally {
      loading.value = false
    }
  }

  function reset() {
    selectedUserId.value = null
    dialogOpen.value = false
    loading.value = false
    error.value = null
    subject.value = null
    html.value = null
  }

  return {selectedUserId, dialogOpen, loading, error, subject, html, runPreview, reset}
}
