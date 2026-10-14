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

/**
 * A file download's answer with its refusal read as JSON. A file is asked for as a Blob, so a
 * refusal arrives as a Blob too, and its code would never reach the sentence that names it.
 */
export async function readBlobRefusal<T>(call: Promise<Answer<T>>): Promise<Answer<T>> {
  const res = await call
  if (!(res.error instanceof Blob)) return res
  try {
    return {...res, error: JSON.parse(await res.error.text()) as unknown}
  } catch {
    return {...res, error: null}
  }
}
