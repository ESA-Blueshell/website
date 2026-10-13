/**
 * Exceptions domain adapter: the only file in this domain that imports from @/services/api
 * (frontend ADR-002).
 */
import {findException, listExceptions, resolveException, type RecordedException} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import {refusalReader} from "@/utils/refusals"

export type {RecordedException}

const {refusable} = refusalReader({})

/** Every recorded fault, newest first; `resolved` narrows to resolved or open ones. Nothing where the list could not be read. */
export const loadExceptions = (resolved: boolean | null): Promise<RecordedException[] | null> =>
  readOr(listExceptions({query: resolved === null ? {} : {resolved}}), null)

/** One fault with its latest stack trace. `missing` is the api saying it has none, which a read that failed is not. */
export async function readException(id: number): Promise<{fault: RecordedException | null; missing: boolean}> {
  const res = await findException({path: {id}})
  return {fault: res.error || res.data == null ? null : res.data, missing: res.status === 404}
}

/** Marks a fault resolved until it fires again. */
export const markResolved = (id: number): Promise<{ok: true} | Refused> =>
  refusable(resolveException({path: {id}}), "That exception could not be marked resolved.")
