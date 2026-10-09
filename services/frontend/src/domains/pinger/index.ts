/**
 * The pinger domain's public API: a page may only enter the domain through this file
 * (frontend ADR-001).
 */
export type {Box, Motion, MotionMode, PaintJob, PaintSettings, Placement, Refused} from "./adapters/pinger"
export {
  DEFAULT_PAINT,
  addPlacement,
  loadPaintJob,
  movePlacement,
  openPaintStream,
  removePlacement,
  saveSettings,
  setMotion,
  storePaintImage,
} from "./adapters/pinger"
export {CANVAS_H, CANVAS_W, clockOffset, isMoving, positionAt, reflect, useServerClock} from "./motion"
export type {CombinedRecord, FastestStanding, HouseLine, Leaderboard, Standing} from "./adapters/leaderboard"
export {EMPTY_LEADERBOARD, loadLeaderboard, openLeaderboardStream, ownStanding} from "./adapters/leaderboard"
export type {AppOs} from "./adapters/download"
export {appDownloadUrl} from "./adapters/download"
export {SNTPINGS_ENABLED, SNTPINGS_PATH, SNTPINGS_STREAM_URL} from "./sntpings"
export {compactRate, setAt} from "./rates"
// Re-exported so the page resolves a stored image path against the api without reaching the
// generated client itself (frontend ADR-001).
export {apiUrl} from "@/services/api"
