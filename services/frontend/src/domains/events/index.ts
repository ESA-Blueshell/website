/**
 * The events domain's public API: what a page or a shared component may reach for, and nothing
 * else (frontend ADR-001). Re-exported by name, because the list of names is the promise.
 */
export {readEvent} from "./adapters/events"
export {listEventSignUps, removeSignUp, saveSignUpAsBoard} from "./adapters/signUps"
export {
  EventSignUpKind,
  QuestionType,
  type AnswerRequest,
  type EventResponse,
  type EventSignUpResponse,
  type QuestionRequest,
  type QuestionResponse,
  type UpdateEventSignUpRequest,
} from "@/services/api"
