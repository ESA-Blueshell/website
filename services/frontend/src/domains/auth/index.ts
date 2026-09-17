/**
 * The auth domain's public API: its own files import each other directly, and anything outside
 * it comes through here (frontend ADR-001).
 */
export {signIn, type SignInResult} from "./adapters/auth"
export type {LoginResponse} from "@/services/api"
