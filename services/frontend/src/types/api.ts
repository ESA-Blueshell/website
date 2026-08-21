/**
 * Widens every property of an API model so it also accepts the `null` the
 * OpenAPI schema allows on optional fields, and makes each one optional.
 * Form models built out of generated request and response types need this:
 * a request type requires the field, the matching response type reports it
 * as nullable, and the intersection of the two rejects both halves.
 */
export type PartialNullable<T> = {[K in keyof T]?: T[K] | null}
