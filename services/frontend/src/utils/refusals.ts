import type {Refused} from "@/types/api"
import type {Answer} from "@/utils/answers"

/** The one key the reader itself reads; a domain's body type adds the facts its own codes name. */
export interface RefusalCode {
  code?: string
}

/** A write that landed, carrying what the api answered with. */
export interface Saved<T> {
  ok: true
  saved: T
}

export interface RefusalReader {
  /** The domain's own sentence for a refusal, or null for a code it has not been taught. */
  sentenceFor: (body: unknown) => string | null
  /** The sentence a reader sees for a refused write. */
  reasonFor: (error: unknown, fallback: string) => string
  /**
   * A write that answers with a record, as its envelope. An answer carrying no record is refused
   * too: a caller handed nothing would go on to write against nothing.
   */
  refusable: <T>(call: Promise<Answer<T>>, fallback: string) => Promise<Saved<T> | Refused>
  /** A write whose success carries nothing, a removal or a drop, as its envelope. */
  accepted: (call: Promise<Answer<unknown>>, fallback: string) => Promise<{ok: true} | Refused>
}

/**
 * Reads a refusal body into the sentence a reader sees, for a domain's map of codes.
 *
 * The api answers a refused write with a code and the facts about it rather than finished prose
 * (ADR-026), so what is left to decide is the order to read a body in, and that order is the
 * same wherever a refusal is shown: the sentence the domain composes, then the field violations,
 * then the api's fixed summary, then the caller's fallback. It lives in `utils` rather than in
 * either domain because both meet the same envelope, and giving one domain the definition would
 * make the other deep-import a domain internal, which ADR-001 forbids. The `sentences` map stays
 * the domain's own — its codes are its own, and nothing shared should know them.
 */
export function refusalReader<B extends RefusalCode>(
  sentences: Record<string, (refusal: B) => string>,
): RefusalReader {
  const sentenceFor = (body: unknown): string | null => {
    const refusal = body as B | null | undefined
    const code = refusal?.code
    if (!code) return null
    return sentences[code]?.(refusal as B) ?? null
  }

  // Composed sentence, then validation errors, then the api's fixed summary, then the fallback.
  const reasonFor = (error: unknown, fallback: string): string => {
    const body = error as {detail?: string; title?: string; errors?: Array<{message?: string}>} | null
    const fields = body?.errors?.map(one => one?.message).filter(Boolean).join(". ")
    return sentenceFor(error) || fields || body?.detail || body?.title || fallback
  }

  const refusable = async <T>(call: Promise<Answer<T>>, fallback: string): Promise<Saved<T> | Refused> => {
    const res = await call
    if (res.error || res.data == null) return {ok: false, reason: reasonFor(res.error, fallback)}
    return {ok: true, saved: res.data}
  }

  const accepted = async (call: Promise<Answer<unknown>>, fallback: string): Promise<{ok: true} | Refused> => {
    const res = await call
    if (res.error) return {ok: false, reason: reasonFor(res.error, fallback)}
    return {ok: true}
  }

  return {sentenceFor, reasonFor, refusable, accepted}
}
