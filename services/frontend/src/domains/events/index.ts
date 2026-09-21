/**
 * The events domain's public API: what a page or a shared component may reach for, and nothing
 * else (frontend ADR-001). Re-exported by name, because the list of names is the promise.
 */
export {listEventSignUps, removeSignUp, saveSignUpAsBoard} from "./adapters/signUps"
export {
  EventSignUpKind,
  type EventResponse,
  type EventSignUpResponse,
  type UpdateEventSignUpRequest,
} from "@/services/api"
