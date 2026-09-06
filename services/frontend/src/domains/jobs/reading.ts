/**
 * How a job execution reads: the words on a row and the colour behind it.
 *
 * Knowledge about jobs rather than about a page, so it sits in the domain and can be checked
 * without mounting anything.
 */
import type {Job, JobRelatedEntity, JobStats} from "./adapters/jobs"
import {JobExecutionCategory, JobExecutionStatus} from "./adapters/jobs"
import {jobCatalogEntry} from "@/utils/jobCatalog"

/** `contact.sync_user` reads as `Contact Sync User`. */
export function titleCase(value: string): string {
  return value
    .replace(/[_.-]/g, " ")
    .split(/\s+/)
    .filter(Boolean)
    .map(token => token.charAt(0).toUpperCase() + token.slice(1).toLowerCase())
    .join(" ")
}

/** What the job is, and the first thing it was run against where it names one. */
export function summarizeExecution(job: Job): string {
  const title = jobCatalogEntry(job.jobType ?? "").title
  const primary = job.relatedEntities?.[0]?.label
  return primary ? `${title} — ${primary}` : title
}

/** The heading on a collapsed row, falling back to the category where the catalog knows nothing. */
export function previewTitle(job: Job): string {
  const summary = summarizeExecution(job).trim()
  return summary || `${titleCase(job.category ?? "job")} job`
}

export function jobDescription(job: Job): string {
  return jobCatalogEntry(job.jobType ?? "").description
}

export function errorSummary(job: Job): string {
  return job.errorMessage ?? job.errorReason ?? "-"
}

/** A Java stack trace rather than a sentence, read off the shape of it. */
export function looksLikeStackTrace(value?: string | null): boolean {
  if (!value) return false
  return value.includes("\n\tat ") || value.includes("\n at ") || value.includes("Caused by:")
}

export function hasStackTrace(job: Job): boolean {
  return !!job.stackTrace || looksLikeStackTrace(job.errorReason)
}

/** The trace as stored, or the error reason where the api put one in that field instead. */
export function stackTrace(job: Job): string {
  if (job.stackTrace) return job.stackTrace
  if (job.errorReason && looksLikeStackTrace(job.errorReason)) return job.errorReason
  return ""
}

/** Whoever asked for this job, at full length: a detail panel has the room for the handle. */
export function actorDisplay(job: Job): string {
  if (job.initiatedByDisplay) return job.initiatedByDisplay
  if (job.initiatedByFullName && job.initiatedByUsername) {
    return `${job.initiatedByFullName} (@${job.initiatedByUsername})`
  }
  if (job.initiatedByType === "SYSTEM") return "System"
  if (job.initiatedByUserId != null) return `User #${job.initiatedByUserId}`
  return "System"
}

/** The same person on a collapsed row, where the trailing handle costs more room than it earns. */
export function previewActorDisplay(job: Job): string {
  if (job.initiatedByFullName?.trim()) return job.initiatedByFullName
  if (job.initiatedByDisplay?.trim()) {
    return job.initiatedByDisplay.replace(/\s*\(@[^)]+\)\s*$/, "")
  }
  if (job.initiatedByType === "SYSTEM") return "System"
  if (job.initiatedByUserId != null) return `User #${job.initiatedByUserId}`
  return "System"
}

export function relatedEntityLabel(entity: JobRelatedEntity): string {
  const type = titleCase(entity.type ?? "entity")
  return entity.label ?? `${type} #${entity.id}`
}

export function relatedEntityTypeLabel(type?: string | null): string {
  return titleCase(type ?? "entity")
}

export function statusTitle(status?: string | null): string {
  return status ? titleCase(status) : "Unknown"
}

export function statusColor(status?: string | null): string {
  if (status === "SUCCESS") return "success"
  if (status === "FAILED") return "error"
  if (status === "RUNNING") return "info"
  if (status === "QUEUED") return "warning"
  return "secondary"
}

export function rowStatusClass(status?: string | null): string {
  if (status === "SUCCESS") return "job-row--success"
  if (status === "FAILED") return "job-row--failed"
  if (status === "RUNNING") return "job-row--running"
  if (status === "QUEUED") return "job-row--queued"
  return ""
}

/** A job that can be run again: only the two states that stopped without succeeding. */
export function canRetry(job: Job): boolean {
  return job.id != null && (job.status === "FAILED" || job.status === "DEAD")
}

export function successRate(stats: JobStats | null): number {
  if (!stats || stats.totalCount === 0) return 0
  return Math.round(stats.successCount / stats.totalCount * 100)
}

/**
 * The chip counts, read off the stats endpoint rather than off the loaded page, so they are the
 * database's totals. Zeroes while the stats are still loading, which keeps the chip row mounted.
 */
export function statusCounts(stats: JobStats | null): Record<JobExecutionStatus, number> {
  return {
    [JobExecutionStatus.QUEUED]: stats?.queuedCount ?? 0,
    [JobExecutionStatus.RUNNING]: stats?.runningCount ?? 0,
    [JobExecutionStatus.SUCCESS]: stats?.successCount ?? 0,
    [JobExecutionStatus.FAILED]: stats?.failedCount ?? 0,
    [JobExecutionStatus.DEAD]: stats?.deadCount ?? 0,
  }
}

/** One option in a filter picker: what it says, and the value it filters by. */
export interface FilterOption {
  title: string
  value: string
}

/**
 * The filters offered, built from the generated enums rather than from the rows on screen, so a
 * category with nothing in it today is still selectable.
 */
export const categoryOptions = (): FilterOption[] => [
  {title: "All categories", value: "all"},
  ...Object.values(JobExecutionCategory).map(value => ({title: titleCase(value), value})),
]

export const statusOptions = (): FilterOption[] => [
  {title: "All statuses", value: "all"},
  ...Object.values(JobExecutionStatus).map(value => ({title: titleCase(value), value})),
]
