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

/** Every recorded fault, newest first; `resolved` narrows to resolved or open ones. Empty where unreadable. */
export const loadExceptions = (resolved: boolean | null): Promise<RecordedException[]> =>
  readOr(listExceptions({query: resolved === null ? {} : {resolved}}), [])

/** One fault with its latest stack trace, or nothing where it could not be read. */
export const loadException = (id: number): Promise<RecordedException | null> =>
  readOr(findException({path: {id}}), null)

/** Marks a fault resolved until it fires again. */
export const markResolved = (id: number): Promise<{ok: true} | Refused> =>
  refusable(resolveException({path: {id}}), "That exception could not be marked resolved.")
