/**
 * The board domain's public API — what a page, a component or another domain may reach for, and
 * nothing else. Frontend ADR-001 puts one of these on every domain, after the pattern
 * `domains/user/index.ts` set: the domain's own files import each other directly, anything
 * outside it comes through here.
 *
 * Only the reading rules so far, which is the point of them: how a board's number, year and seats
 * read is knowledge about boards, so it belongs to this domain rather than to a page — and it
 * cannot live under `src/components/`, where a shared component may not know about a domain at
 * all. The adapter is not listed because nothing outside the domain has needed the wire yet, and
 * a public API is a promise rather than an index.
 *
 * Re-exported by name rather than with `export *`, because the list of names is the promise.
 */
export {academicYear, boardEyebrow, boardName, romanNumeral} from "./reading"
export {
  bySeatRank,
  SEAT_OFFICES,
  type Seated,
  seatRank,
  seatsInOrder,
  UNRANKED_SEAT,
} from "./seatOrder"
