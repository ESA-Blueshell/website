import {computed, ref} from "vue"
import {type Alert, loadAlerts} from "./adapters/alerts"

// One list for the whole of Management, so the sidebar, the phone bar, the account menu and the
// page count the same alerts.
const alerts = ref<Alert[]>([])

/** The reader's alerts, shared by every part of Management that counts them. */
export function useAlerts() {
  const refresh = async () => {
    alerts.value = await loadAlerts()
  }
  const shown = computed(() => alerts.value.filter((alert) => !alert.hidden))
  const hidden = computed(() => alerts.value.filter((alert) => alert.hidden))
  return {alerts, shown, hidden, count: computed(() => shown.value.length), refresh}
}
