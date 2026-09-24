/**
 * The games domain's public API: its own files import each other directly, and anything outside
 * it comes through here (frontend ADR-001).
 */
export {cellOf, driftItemOf, forgetCasualGames, initialsOf, reelItemOf, useCasualGames} from "./useCasualGames"
export {type CasualGame} from "./adapters/games"
export {useMayEditGames} from "./island/useMayEditGames"
