import {computed, ref} from "vue"
import {type Alert, loadAlerts} from "./adapters/alerts"

// One list for the whole of Management, so the sidebar, the phone bar, the account menu and the
// page count the same alerts.
const alerts = ref<Alert[]>([])
const unread = ref(false)
// The shell, the page and a hide all ask, and an older answer must not undo a newer one.
let asked = 0

/** The reader's alerts, shared by every part of Management that counts them. */
export function useAlerts() {
  // A read that failed keeps the alerts it had: a count that drops to nothing reads as all dealt with.
  const refresh = async () => {
    const mine = ++asked
    const read = await loadAlerts()
    if (mine !== asked) return
    unread.value = read === null
    if (read) alerts.value = read
  }
  /** Lets go of what was read, so the next person to sign in never meets this one's alerts. */
  const forget = () => {
    asked++
    alerts.value = []
    unread.value = false
  }
  const shown = computed(() => alerts.value.filter((alert) => !alert.hidden))
  const hidden = computed(() => alerts.value.filter((alert) => alert.hidden))
  return {alerts, shown, hidden, unread, count: computed(() => shown.value.length), refresh, forget}
}
