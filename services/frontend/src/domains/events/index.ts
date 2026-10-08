/**
 * The events domain's public API: what a page or a shared component may reach for, and nothing
 * else (frontend ADR-001). Re-exported by name, because the list of names is the promise.
 */
export {
  deleteEvent,
  type EventPage,
  type EventQuery,
  eventFileUrl,
  listEvents,
  readEvent,
  readRoster,
  type EventRoster,
  readEventBanner,
  readEventPage,
  saveEvent,
  saveEventBanner,
  saveNewEvent,
  setEventApproved,
} from "./adapters/events"
export {isOnline, noSignUpsOf, plateOf, whenOf} from "./island/eventFacts"
export {useEventReader} from "./island/useEventReader"
export {downloadIcs, pageUrlOf} from "./island/eventCalendar"
export {useAnnouncePrompt} from "./island/announcing"
export {
  addSignUpAsBoard,
  changeOwnSignUp,
  listEventSignUps,
  listOwnSignUps,
  listSignUpsByAccessToken,
  removeSignUp,
  type SavedSignUp,
  saveSignUpAsBoard,
  signUpForEvent,
  withdrawSignUp,
} from "./adapters/signUps"
export {
  EventSignUpKind,
  QuestionType,
  type AnswerRequest,
  type CreateEventRequest,
  type CreateEventSignUpRequest,
  type CreateGuestRequest,
  type EventBannerRequest,
  type EventResponse,
  type EventSignUpResponse,
  type GuestResponse,
  type PageMetadata,
  type QuestionRequest,
  type QuestionResponse,
  type SurveyRequest,
  type SurveyResponse,
  type UpdateEventRequest,
  type UpdateEventSignUpRequest,
} from "@/services/api"
