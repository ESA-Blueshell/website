import type {VotePickResponse} from "@/services/api"

/** One pick as the page holds it: a game, and its track where the vote's kind picks one. */
export type BallotPick = VotePickResponse

/** What the pick at [index] (0 for first) is worth under [points]. */
export const pointsAt = (points: number[], index: number): number => points[index] ?? 0

/**
 * The picks with [pick] added: in place of a pick of the same game, so a ballot names a game once,
 * or at the end. A ballot already holding [max] picks takes no new game.
 */
export function withPick(picks: BallotPick[], pick: BallotPick, max: number): BallotPick[] {
  const same = picks.findIndex(one => one.candidate.id === pick.candidate.id)
  if (same >= 0) return picks.map((one, index) => (index === same ? pick : one))
  return picks.length >= max ? picks : [...picks, pick]
}

/** The picks with the one at [index] moved by [step], one place up (-1) or down (1). */
export function moved(picks: BallotPick[], index: number, step: -1 | 1): BallotPick[] {
  const to = index + step
  if (to < 0 || to >= picks.length) return picks
  const next = [...picks]
  const [pick] = next.splice(index, 1)
  next.splice(to, 0, pick!)
  return next
}

/** The picks without the one at [index]. */
export const without = (picks: BallotPick[], index: number): BallotPick[] => picks.filter((_, at) => at !== index)

/** Whether two ballots name the same games and tracks in the same order. */
export const samePicks = (a: BallotPick[], b: BallotPick[]): boolean =>
  a.length === b.length && a.every((pick, index) => pick.candidate.id === b[index]!.candidate.id && pick.track?.id === b[index]!.track?.id)

