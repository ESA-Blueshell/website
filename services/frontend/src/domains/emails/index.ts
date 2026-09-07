/**
 * The emails domain's public API: its own files import each other directly, and anything outside
 * it comes through here (frontend ADR-001).
 *
 * The wire is listed here too. It used to be left out so that only the manager page reached
 * the adapter at its own path, but a page may not enter a domain except through this file
 * (#1167) — and the door is what makes the adapter's own layout the domain's business.
 *
 * Re-exported by name rather than with `export *`, because the list of names is the promise.
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
export {loadEmailPage, loadEmailStats, readSentEmail, retrySend} from "./adapters/emails"
