/**
 * Jobs domain adapter: the only file in this domain that imports from @/services/api
 * (frontend ADR-002). Everything else imports from here.
 */
import {
  getStats,
  list,
  retry,
  type JobExecution,
  type JobStatsDto,
  JobExecutionCategory,
  JobExecutionStatus,
} from "@/services/api"
import type {PageOf, PageQuery} from "@/composables/usePagedTable"
import type {Refused} from "@/types/api"
import {refusalReader} from "@/utils/refusals"

// Re-exported so this adapter still answers for its own surface, while the type has one definition.
export type {Refused}

export type Job = JobExecution
export type JobStats = JobStatsDto
export type JobRelatedEntity = NonNullable<Job["relatedEntities"]>[number]
export {JobExecutionCategory, JobExecutionStatus}

/**
 * The api declares no refusal codes for this module, so a refused job write reads as whatever
 * detail it carried; the sentence map stays empty rather than inventing codes it does not send.
 */
const {reasonFor} = refusalReader({})

/** What the manager narrows a page of jobs by. Everything is optional: none is the whole list. */
export interface JobFilter {
  category?: JobExecutionCategory
  status?: JobExecutionStatus
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
  const res = await list({
    query: {
      page: query.page,
      size: query.size,
      sort: JOB_SORT,
      ...(filter.category ? {category: filter.category} : {}),
      ...(filter.status ? {status: filter.status} : {}),
      ...(query.search ? {search: query.search} : {}),
    },
  })
  if (res.error || !res.data) return emptyPage

  const data = res.data as unknown
  if (Array.isArray(data)) {
    const all = data as Job[]
    const start = query.page * query.size
    return {
      rows: all.slice(start, start + query.size),
      totalElements: all.length,
      totalPages: Math.max(1, Math.ceil(all.length / query.size)),
    }
  }

  const rows = res.data.content ?? []
  const totalElements = res.data.page?.totalElements ?? rows.length
  return {
    rows,
    totalElements,
    totalPages: Math.max(1, res.data.page?.totalPages ?? Math.ceil(totalElements / query.size)),
  }
}

/** The counts behind the stats panel, or nothing where they could not be read — it is supplementary. */
export async function loadJobStats(): Promise<JobStats | null> {
  const res = await getStats()
  return res.error ? null : res.data ?? null
}

/**
 * Queues a failed job for another attempt.
 *
 * Answers with the api's own words when it says no, because pressing Retry and being told
 * nothing is indistinguishable from pressing nothing at all.
 */
export async function retryJob(id: number): Promise<{ok: true} | Refused> {
  const res = await retry({path: {id}})
  if (res.error || !res.data) {
    return {ok: false, reason: reasonFor(res.error, "That job could not be retried.")}
  }
  return {ok: true}
}
