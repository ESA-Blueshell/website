/**
 * Widens every property of an API model so it also accepts the `null` the
 * OpenAPI schema allows on optional fields, and makes each one optional.
 * Form models built out of generated request and response types need this:
 * a request type requires the field, the matching response type reports it
 * as nullable, and the intersection of the two rejects both halves.
 */
export type PartialNullable<T> = {[K in keyof T]?: T[K] | null}

/**
 * A write the api refused, in its own words.
 *
 * The sdk hands a refusal back as a body rather than throwing, so a caller that only reads
 * `data` cannot tell a rejection from a success and a `try`/`catch` catches nothing (ADR-002).
 * It lives here rather than in a domain because every domain's adapter answers with it, and
 * one domain reaching into another's for it is the deep import ADR-001 forbids.
 */
export interface Refused {
  ok: false
  reason: string
}
