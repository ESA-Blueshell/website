import type {RecordedException} from "./adapters/exceptions"

/** `java.lang.IllegalStateException` reads as `IllegalStateException`: the package says nothing new. */
export const shortType = (type: string): string => type.slice(type.lastIndexOf(".") + 1)

/** Where a fault was thrown, as the class and method without the package. */
export const shortPlace = (thrownAt: string): string => {
  const parts = thrownAt.split(".")
  return parts.length > 2 ? parts.slice(-2).join(".") : thrownAt
}

/** Whether a fault matches every word typed, over its type, place, message and what it concerned. */
export const matchesSearch = (fault: RecordedException, search: string): boolean => {
  const haystack = [fault.exceptionType, fault.thrownAt, fault.latestMessage ?? "", fault.latestConcern].join(" ").toLowerCase()
  return search.trim().toLowerCase().split(/\s+/).every((word) => haystack.includes(word))
}

/** The page for what a fault concerned: the job that failed, or nothing for a request. */
export const concernLink = (fault: RecordedException): string | null =>
  fault.latestJobExecutionId == null ? null : `/management/jobs/${fault.latestJobExecutionId}`
