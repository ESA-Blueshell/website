/**
 * The exceptions domain's public API: anything outside it comes through here (frontend ADR-001).
 */
export {concernLink, matchesSearch, shortPlace, shortType} from "./reading"
export type {RecordedException} from "./adapters/exceptions"
export {loadException, loadExceptions, markResolved} from "./adapters/exceptions"
