/**
 * The pinger domain's public API: a page may only enter the domain through this file
 * (frontend ADR-001).
 */
export type {CombinedRecord, FastestStanding, HouseLine, Leaderboard, Standing} from "./adapters/leaderboard"
export {EMPTY_LEADERBOARD, loadLeaderboard, ownStanding} from "./adapters/leaderboard"
export {SNTPINGS_ENABLED, SNTPINGS_PATH} from "./sntpings"
export {compactRate, setAt} from "./rates"
// Re-exported so a board resolves a stored avatar path against the api without reaching the
// generated client itself (frontend ADR-001).
export {apiUrl} from "@/services/api"
