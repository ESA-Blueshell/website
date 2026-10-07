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
  type EmailMoment,
  canResend,
  canRetry,
  emailTypeLabel,
  sentFacts,
  stateKindOf,
  statusWord,
  timelineOf,
} from "./reading"
export type {EmailDetail, EmailFilter, EmailStats, SentEmail} from "./adapters/emails"
export {EmailDeliveryStatus} from "./adapters/emails"
export {loadEmailPage, loadEmailStats, readEmail, readSentEmail, renderWritten, resendEmail, retrySend} from "./adapters/emails"
export type {SendingAddress, SendingAddressRequest} from "./adapters/sendingAddresses"
export {MailSecurity, addAddress, checkAddress, loadSendingAddresses, removeAddress, saveAddress} from "./adapters/sendingAddresses"
export {SITE_SENDER, type CheckState, type MailProtocol, fromOptions, readState, securityLabel, sendState, usualPort} from "./sending"
