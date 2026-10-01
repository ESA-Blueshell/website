/**
 * How a job execution reads: the words on a row and the colour behind it.
 *
 * Knowledge about jobs rather than about a page, so it sits in the domain and can be checked
 * without mounting anything.
 */
import type {Job, JobFoldedTrigger, JobRelatedEntity, JobStats} from "./adapters/jobs"
import {JobEffect, JobExecutionCategory, JobExecutionStatus, JobTrigger} from "./adapters/jobs"
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
  return primary ? `${title}: ${primary}` : title
}

/** The heading on a collapsed row, falling back to the category where the catalog knows nothing. */
export function previewTitle(job: Job): string {
  const summary = summarizeExecution(job).trim()
  return summary || `${titleCase(job.category ?? "job")} job`
}

export function jobDescription(job: Job): string {
  return jobCatalogEntry(job.jobType ?? "").description
}

const TRIGGERS: Record<JobTrigger, string> = {
  [JobTrigger.EVENT_CREATED]: "Creating the event",
  [JobTrigger.EVENT_UPDATED]: "Editing the event",
  [JobTrigger.EVENT_APPROVED]: "Approving the event",
  [JobTrigger.EVENT_SENT_BACK]: "Sending the event back to the board",
  [JobTrigger.EVENT_UNAPPROVED]: "Unapproving the event",
  [JobTrigger.EVENT_DELETED]: "Deleting the event",
  [JobTrigger.SIGN_UPS_CHANGED]: "A change in sign-ups",
  [JobTrigger.USER_CHANGED]: "A change to the account",
  [JobTrigger.USER_REMOVED]: "Removing the account",
  [JobTrigger.MEMBERSHIP_CHANGED]: "A change in a cohort's members",
  [JobTrigger.SITE_ACTION]: "An action on the site",
  [JobTrigger.MORNING_RUN]: "The 08:00 run",
  [JobTrigger.SCHEDULED_RUN]: "A scheduled run",
  [JobTrigger.ANOTHER_JOB]: "Another job",
  [JobTrigger.BY_HAND]: "Run a job",
}

/** What queued the job, and that somebody ran it again by hand; empty on a row too old to say. */
export function triggerLabel(job: Job): string {
  if (!job.trigger) return ""
  const queuedBy = TRIGGERS[job.trigger]
  return job.forced && job.trigger !== JobTrigger.BY_HAND ? `${queuedBy}, run again by hand` : queuedBy
}

/** A trigger folded into a job, in the words its own trigger would have. */
export function foldedTriggerLabel(folded: JobFoldedTrigger): string {
  return TRIGGERS[folded.trigger]
}

/** What a successful run did to the thing its job keeps; empty where it reported nothing. */
export function effectLabel(job: Job): string {
  if (!job.effect) return ""
  const thing = jobCatalogEntry(job.jobType ?? "").thing ?? "what it keeps"
  switch (job.effect) {
    case JobEffect.MADE: return `Put up ${thing}`
    case JobEffect.EDITED: return `Edited ${thing}`
    case JobEffect.UNCHANGED: return `Found ${thing} up to date`
    case JobEffect.REMOVED: return `Took down ${thing}`
  }
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

/** Where the page for what a job concerns lives, or nothing where it has none. */
export function relatedEntityLink(entity: JobRelatedEntity): string | null {
  if (entity.id == null) return null
  if (entity.type === "EVENT") return `/events/${entity.id}`
  if (entity.type === "COHORT") return "/management/platforms/brevo"
  if (entity.type === "USER") return `/management/users/${entity.id}`
  return null
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
  if (status === "SKIPPED") return "grey"
  return "secondary"
}

export function rowStatusClass(status?: string | null): string {
  if (status === "SUCCESS") return "job-row--success"
  if (status === "FAILED") return "job-row--failed"
  if (status === "RUNNING") return "job-row--running"
  if (status === "QUEUED") return "job-row--queued"
  if (status === "SKIPPED") return "job-row--skipped"
  return ""
}

/** A job that can be run again: the states that stopped without doing their work. */
export function canRetry(job: Job): boolean {
  return job.id != null && (job.status === "FAILED" || job.status === "DEAD" || job.status === "SKIPPED")
}

/** What the run-again button says: a skipped job did nothing, so it runs anyway rather than retries. */
export function retryLabel(job: Job): string {
  return job.status === "SKIPPED" ? "Run anyway" : "Retry"
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
    [JobExecutionStatus.SKIPPED]: stats?.skippedCount ?? 0,
    [JobExecutionStatus.FAILED]: stats?.failedCount ?? 0,
    [JobExecutionStatus.DEAD]: stats?.deadCount ?? 0,
  }
}

/** One option in a filter picker: what it says, and the value it filters by. */
export interface FilterOption {
  key: string
  label: string
}

/**
 * The filters offered, built from the generated enums rather than from the rows on screen, so a
 * category with nothing in it today is still selectable.
 */
export const categoryOptions = (): FilterOption[] =>
  Object.values(JobExecutionCategory).map(key => ({key, label: titleCase(key)}))

export const statusOptions = (): FilterOption[] =>
  Object.values(JobExecutionStatus).map(key => ({key, label: titleCase(key)}))
