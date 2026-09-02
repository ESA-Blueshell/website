/**
 * The order a board's members read in, which is seniority.
 *
 * There is no stored order column, deliberately: a role is written in the board's own words —
 * "Secretary and Commissioner of the Esports Lounge" — rather than chosen from a fixed list,
 * because nine years of boards have renamed and combined their offices. An order column would
 * be a second thing to keep right, and the words already say which office a membership held.
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
export const BOARD_OFFICES = ["chair", "secretary", "treasurer", "internal", "external", "esports"] as const

/** Where a role naming none of the offices sorts: after all of them, in one group. */
export const UNRANKED_OFFICE = BOARD_OFFICES.length

/**
 * How senior a role is: 0 for a chair, up to [UNRANKED_OFFICE] for a role the offices do not name.
 *
 * The best rank found anywhere in the string wins, which is what searching the offices in
 * seniority order gives: "Secretary/Treasurer" is a secretary, and "Treasurer and Commissioner of
 * Esports Affairs" a treasurer. The senior office happens to be written first in every role the
 * association has ever recorded, but the rule does not lean on that — a board that writes its
 * commission first still reads under the office that outranks it.
 */
export function memberRank(role?: string | null): number {
  const written = (role ?? "").toLowerCase()
  const found = BOARD_OFFICES.findIndex(office => written.includes(office))
  return found === -1 ? UNRANKED_OFFICE : found
}

/**
 * The least of a board membership the ranking reads: the office it held and the name it stands
 * under. An office is the role in the board's own words, per `docs/CONTEXT.md`.
 *
 * Structural rather than the generated member, so the rule can be read against a plain object and
 * does not move when the wire does.
 */
export interface OfficeHolder {
  role?: string | null
  name?: string | null
}

/**
 * Members by seniority, ties by name.
 *
 * Ties happen (a board with two commissioners of the same office, or two memberships whose roles
 * the offices do not name at all) and by name rather than by the order the api answered in, so
 * the page reads the same way twice running.
 */
export function byMemberRank(left: OfficeHolder, right: OfficeHolder): number {
  const ranked = memberRank(left.role) - memberRank(right.role)
  if (ranked !== 0) return ranked
  return (left.name ?? "").localeCompare(right.name ?? "")
}

/** A board's members in reading order. A copy, because the list is usually a prop. */
export function membersInOrder<T extends OfficeHolder>(members: readonly T[]): T[] {
  return members.slice().sort(byMemberRank)
}
