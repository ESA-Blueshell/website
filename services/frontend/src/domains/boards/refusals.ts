// TWIN: `board/domain/BoardRefusal.kt` declares the codes and their facts. See ADR-026.

const countOf = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

export const boardHoldsMembers = (number: number, members: number) =>
  `Board ${number} still has ${countOf(members, "member", "members")} on it, so it cannot be removed. `
  + "Every member is somebody's place in the association's history. Remove the members first, "
  + "and the board goes with them."

interface RefusalBody {
  code?: string
  number?: number
  members?: number
}

const sentences: Record<string, (r: RefusalBody) => string> = {
  BoardHoldsMembers: r => boardHoldsMembers(r.number ?? 0, r.members ?? 0),
}

export function sentenceFor(body: unknown): string | null {
  const refusal = body as RefusalBody | null | undefined
  const code = refusal?.code
  if (!code) return null
  return sentences[code]?.(refusal as RefusalBody) ?? null
}

// Composed sentence, then validation errors, then the api's fixed summary, then the fallback.
export function reasonFor(error: unknown, fallback: string): string {
  const body = error as {detail?: string; title?: string; errors?: Array<{message?: string}>} | null
  const composed = sentenceFor(error)
  const fields = body?.errors?.map(one => one?.message).filter(Boolean).join(". ")
  return composed || fields || body?.detail || body?.title || fallback
}
