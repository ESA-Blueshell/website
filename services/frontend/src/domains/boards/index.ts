/**
 * The board domain's public API: its own files import each other directly, and anything outside
 * it comes through here (frontend ADR-001).
 *
 * The reading rules belong here rather than to a page, being knowledge about boards, and cannot
 * live under `src/components/`, where a shared component may not know a domain at all. The wire
 * is not listed — a page reaches the adapter at its own path. Re-exported by name rather than
 * with `export *`, because the list of names is the promise.
 */
export {academicYear, boardEyebrow, boardName, romanNumeral} from "./reading"
export {boardStops, boardsEitherSide, type BoardsEitherSide, travelBetween, type Stopped} from "./boardAxis"
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
  type OfficeHolder,
  UNRANKED_OFFICE,
} from "./memberOrder"
export {type AccentInk, BOARD_BLUE, inkOnAccent} from "./accent"
export {nextBoardNumber, type Numbered} from "./numbering"
