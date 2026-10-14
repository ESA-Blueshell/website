/**
 * The emails domain's public API: its own files import each other directly, and anything outside
 * it comes through here (frontend ADR-001).
 *
 * Re-exported by name rather than with `export *`, because the list of names is the promise.
 */
export {
  type EmailMoment,
  canResend,
  canRetry,
  emailTypeLabel,
  sentFacts,
  stateKindOf,
  statusWord,
  timelineOf,
} from "./reading"
export type {EmailDetail, EmailStats, SentEmail} from "./adapters/emails"
export {EmailDeliveryStatus} from "./adapters/emails"
export {loadEmailPage, loadEmailStats, readEmail, readSentEmail, renderWritten, resendEmail, retrySend} from "./adapters/emails"
