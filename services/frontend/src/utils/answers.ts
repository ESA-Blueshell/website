/** What an sdk call resolves to: a body where it landed, an error where it was refused. */
export interface Answer<T> {
  data?: T
  error?: unknown
}

/**
 * A read's body, or the fallback where it was refused or came back empty.
 *
 * The sdk answers a refusal with an `error` rather than throwing, so a read that only looked at
 * `data` could not tell a refusal from a body. The fallback is the caller's: an empty page for a
 * table, null for a panel that says it could not load.
 */
export async function readOr<T, F>(call: Promise<Answer<T>>, fallback: F): Promise<T | F> {
  const res = await call
  return res.error || res.data == null ? fallback : res.data
}
