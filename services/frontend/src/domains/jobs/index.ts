/**
 * The jobs domain's public API: its own files import each other directly, and anything outside
 * it comes through here (frontend ADR-001).
 *
 * The wire is not listed — the manager page reaches the adapter at its own path, which is the one
 * place a call to the api may be written. Re-exported by name rather than with `export *`,
 * because the list of names is the promise.
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
