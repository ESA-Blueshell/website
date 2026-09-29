/**
 * Jobs domain adapter: the only file in this domain that imports from @/services/api
 * (frontend ADR-002). Everything else imports from here.
 */
import {
  enqueue,
  getStats,
  jobTypes,
  type JobTypeDescriptor,
  list,
  retry,
  type JobExecution,
  type JobStatsDto,
  JobExecutionCategory,
  JobExecutionStatus,
  JobEffect,
  JobTrigger,
} from "@/services/api"
import type {PageOf, PageQuery} from "@/composables/usePagedTable"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import {refusalReader} from "@/utils/refusals"

// Re-exported so this adapter still answers for its own surface, while the type has one definition.
export type {Refused}

export type Job = JobExecution
export type JobStats = JobStatsDto
export type JobRelatedEntity = NonNullable<Job["relatedEntities"]>[number]
export type JobFoldedTrigger = Job["foldedTriggers"][number]
export {JobEffect, JobExecutionCategory, JobExecutionStatus, JobTrigger}

/**
 * The api declares no refusal codes for this module, so a refused job write reads as whatever
 * detail it carried; the sentence map stays empty rather than inventing codes it does not send.
 */
const {refusable} = refusalReader({})

/** What the manager narrows a page of jobs by. Everything is optional: none is the whole list. */
export interface JobFilter {
  category?: JobExecutionCategory
  status?: JobExecutionStatus
  hideSkipped?: boolean
}

/** Newest first, and by id where two share a moment, so paging cannot show a row twice. */
const JOB_SORT = ["updatedAt,desc", "id,desc"]

const emptyPage: PageOf<Job> = {rows: [], totalElements: 0, totalPages: 1}

/**
 * One page of job executions.
 *
 * A page that could not be read answers as an empty one: the manager's job is to show what the
 * api will say, and a table of nothing is the honest reading of an api that said nothing.
 *
 * Two answers are accepted because two have been seen. A `PagedModel` is what the endpoint
 * declares; a bare array is the older shape, and is paged here so the table above cannot tell
 * the difference.
 */
export async function loadJobPage(query: PageQuery, filter: JobFilter = {}): Promise<PageOf<Job>> {
  const page = await readOr(list({
    query: {
      page: query.page,
      size: query.size,
      sort: JOB_SORT,
      ...(filter.category ? {category: filter.category} : {}),
      ...(filter.status ? {status: filter.status} : {}),
      ...(filter.hideSkipped ? {hideSkipped: true} : {}),
      ...(query.search ? {search: query.search} : {}),
    },
  }), null)
  if (!page) return emptyPage

  const data = page as unknown
  if (Array.isArray(data)) {
    const all = data as Job[]
    const start = query.page * query.size
    return {
      rows: all.slice(start, start + query.size),
      totalElements: all.length,
      totalPages: Math.max(1, Math.ceil(all.length / query.size)),
    }
  }

  const rows = page.content ?? []
  const totalElements = page.page?.totalElements ?? rows.length
  return {
    rows,
    totalElements,
    totalPages: Math.max(1, page.page?.totalPages ?? Math.ceil(totalElements / query.size)),
  }
}

/** The counts behind the stats panel, or nothing where they could not be read — it is supplementary. */
export const loadJobStats = (): Promise<JobStats | null> => readOr(getStats(), null)

/**
 * Queues a failed job for another attempt.
 *
 * Answers with the api's own words when it says no, because pressing Retry and being told
 * nothing is indistinguishable from pressing nothing at all.
 */
export const retryJob = (id: number): Promise<{ok: true} | Refused> =>
  refusable(retry({path: {id}}), "That job could not be retried.")

/** Every job that can be triggered by hand, with the payload each one takes. Throws on a refusal. */
export async function listJobTypes(): Promise<JobTypeDescriptor[]> {
  const res = await jobTypes({throwOnError: true})
  return res.data ?? []
}

/**
 * Queues one job with the payload the dialog built.
 *
 * Answers with the api's own words when it says no, as retrying does: pressing Trigger and
 * being told nothing is indistinguishable from pressing nothing at all.
 */
export const enqueueJob = (jobType: string, payload: Record<string, unknown>): Promise<{ok: true} | Refused> =>
  refusable(enqueue({body: {jobType, payload}}), "That job could not be triggered.")
