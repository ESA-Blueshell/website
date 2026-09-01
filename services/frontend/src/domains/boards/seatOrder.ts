/**
 * The order a board's seats read in, which is seniority.
 *
 * There is no stored order column, deliberately: a role is written in the board's own words —
 * "Secretary and Commissioner of the Esports Lounge" — rather than chosen from a fixed list,
 * because nine years of boards have renamed and combined their offices. An order column would
 * be a second thing to keep right, and the words already say which office a seat held.
 *
 * So the rank is read out of the role string, and the ranking is a display rule: it decides what
 * a reader meets first, not what is true about a board. It imports nothing (frontend ADR-001) —
 * a role and a name are the whole input.
 */

/**
 * The offices, most senior first. A role is ranked by which of these words it contains.
 *
 * One word each, and the least of the word that identifies the office: `chair` catches both
 * "Chair" and "Chairman", `internal` catches both a Commissioner and an Officer of Internal
 * Affairs, and matching case-insensitively catches the two boards that wrote their commissioners
 * in lower case.
 */
export const SEAT_OFFICES = ["chair", "secretary", "treasurer", "internal", "external", "esports"] as const

/** Where a role naming none of the offices sorts: after all of them, in one group. */
export const UNRANKED_SEAT = SEAT_OFFICES.length

/**
 * How senior a role is: 0 for a chair, up to [UNRANKED_SEAT] for a role the offices do not name.
 *
 * The best rank found anywhere in the string wins, which is what searching the offices in
 * seniority order gives: "Secretary/Treasurer" is a secretary, and "Treasurer and Commissioner of
 * Esports Affairs" a treasurer. The senior office happens to be written first in every role the
 * association has ever recorded, but the rule does not lean on that — a board that writes its
 * commission first still reads under the office that outranks it.
 */
export function seatRank(role?: string | null): number {
  const written = (role ?? "").toLowerCase()
  const found = SEAT_OFFICES.findIndex(office => written.includes(office))
  return found === -1 ? UNRANKED_SEAT : found
}

/**
 * The least of a seat the ranking reads: the role it held and the name it stands under.
 *
 * Structural rather than the generated seat, so the rule can be read against a plain object and
 * does not move when the wire does.
 */
export interface Seated {
  role?: string | null
  name?: string | null
}

/**
 * Seats by seniority, ties by name.
 *
 * Ties happen — a board with two commissioners of the same office, or two seats whose roles the
 * offices do not name at all — and by name rather than by the order the api answered in, so the
 * page reads the same way twice running.
 */
export function bySeatRank(left: Seated, right: Seated): number {
  const ranked = seatRank(left.role) - seatRank(right.role)
  if (ranked !== 0) return ranked
  return (left.name ?? "").localeCompare(right.name ?? "")
}

/** A board's seats in reading order. A copy, because the list is usually a prop. */
export function seatsInOrder<T extends Seated>(seats: readonly T[]): T[] {
  return seats.slice().sort(bySeatRank)
}
