/**
 * The pinger domain's public API: a page may only enter the domain through this file
 * (frontend ADR-001).
 */
export type {PaintJob, PaintRequest, Refused} from "./adapters/pinger"
export {DEFAULT_PAINT, loadPaintJob, savePaintJob, storePaintImage} from "./adapters/pinger"
// Re-exported so the page resolves a stored image path against the api without reaching the
// generated client itself (frontend ADR-001).
export {apiUrl} from "@/services/api"
