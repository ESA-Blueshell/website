/**
 * The alerts domain's public API: anything outside it comes through here (frontend ADR-001).
 */
export {alertLink, alertRow, alertTitle} from "./reading"
export {useAlerts} from "./useAlerts"
export type {Alert} from "./adapters/alerts"
export {AlertKind, hideAlert, loadAlerts, showAlert} from "./adapters/alerts"
