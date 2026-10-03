/**
 * Alerts domain adapter: the only file in this domain that imports from @/services/api
 * (frontend ADR-002).
 */
import {type Alert, AlertKind, hideAlert as hide, listAlerts, showAlert as show} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import {refusalReader} from "@/utils/refusals"

export type {Alert}
export {AlertKind}

const {accepted} = refusalReader({})

/** The alerts this reader may act on, hidden ones marked. None where they could not be read. */
export const loadAlerts = (): Promise<Alert[]> => readOr(listAlerts(), [])

/** Hides one alert for this reader only. */
export const hideAlert = (key: string): Promise<{ok: true} | Refused> =>
  accepted(hide({body: {key}}), "That alert could not be hidden.")

/** Shows a hidden alert to this reader again. */
export const showAlert = (key: string): Promise<{ok: true} | Refused> =>
  accepted(show({body: {key}}), "That alert could not be shown again.")
