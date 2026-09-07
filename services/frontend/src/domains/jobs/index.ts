/**
 * The jobs domain's public API: its own files import each other directly, and anything outside
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
  actorDisplay,
  canRetry,
  categoryOptions,
  errorSummary,
  hasStackTrace,
  jobDescription,
  looksLikeStackTrace,
  previewActorDisplay,
  previewTitle,
  relatedEntityLabel,
  relatedEntityTypeLabel,
  rowStatusClass,
  stackTrace,
  statusColor,
  statusCounts,
  statusOptions,
  statusTitle,
  successRate,
  summarizeExecution,
  titleCase,
} from "./reading"
export {
  type PayloadChip,
  SUPPRESSED_PAYLOAD_KEYS,
  formatPayloadValue,
  humanizeFieldName,
  isSensitiveKey,
  isUninterestingValue,
  payloadChips,
} from "./payload"
export type {Job, JobFilter, JobRelatedEntity, JobStats} from "./adapters/jobs"
export {JobExecutionCategory, JobExecutionStatus} from "./adapters/jobs"
export {loadJobPage, loadJobStats, retryJob} from "./adapters/jobs"
