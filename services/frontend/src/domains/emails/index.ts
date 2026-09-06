/**
 * The emails domain's public API: its own files import each other directly, and anything outside
 * it comes through here (frontend ADR-001).
 *
 * The wire is not listed — the manager page reaches the adapter at its own path, which is the one
 * place a call to the api may be written. Re-exported by name rather than with `export *`,
 * because the list of names is the promise.
 */
export {
  type FilterOption,
  canRetry,
  deliveryRate,
  openRate,
  rowStatusClass,
  statusColor,
  statusCounts,
  statusOptions,
} from "./reading"
export type {EmailFilter, EmailStats, SentEmail} from "./adapters/emails"
export {EmailDeliveryStatus} from "./adapters/emails"
