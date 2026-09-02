/**
 * The board domain's public API — what a page, a component or another domain may reach for, and
 * nothing else. Frontend ADR-001 puts one of these on every domain, after the pattern
 * `domains/user/index.ts` set: the domain's own files import each other directly, anything
 * outside it comes through here.
 *
 * The reading rules, which is the point of them: how a board's number, year and members read, and
 * where a board stands today, is knowledge about boards, so it belongs to this domain rather than
 * to a page — and it cannot live under `src/components/`, where a shared component may not know
 * about a domain at all.
 *
 * The wire is not listed. A page reaches the adapter at its own path, the way the esports pages
 * do, because what a page needs of it changes with the page and a public API is a promise rather
 * than an index.
 *
 * Re-exported by name rather than with `export *`, because the list of names is the promise.
 */
export {academicYear, boardEyebrow, boardName, romanNumeral} from "./reading"
export {boardStops, travelBetween, type Stopped} from "./boardAxis"
export {boardInRoute} from "./boardInRoute"
export {
  type BoardStanding,
  boardInOffice,
  isCandidate,
  standingOf,
  type Termed,
} from "./standing"
export {
  BOARD_OFFICES,
  byMemberRank,
  memberRank,
  membersInOrder,
  type Officed,
  UNRANKED_OFFICE,
} from "./memberOrder"
export {type AccentInk, BOARD_BLUE, inkOnAccent} from "./accent"
export {nextBoardNumber, type Numbered} from "./numbering"
