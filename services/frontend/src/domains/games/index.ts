/**
 * The games domain's public API: its own files import each other directly, and anything outside
 * it comes through here (frontend ADR-001).
 */
export {cellOf, driftItemOf, forgetCasualGames, reelItemOf, useCasualGames} from "./useCasualGames"
export {addGameChannel, type CasualGame} from "./adapters/games"
