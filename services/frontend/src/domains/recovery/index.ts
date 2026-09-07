/**
 * The recovery domain's public API: its own files import each other directly, and anything
 * outside it comes through here (frontend ADR-001).
 */
export {listPendingActivations} from "./adapters/recovery"
export {TokenPurpose} from "@/services/api"
