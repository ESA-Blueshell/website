/**
 * Event sign-up adapter — the only file in this domain that reaches the generated client
 * (frontend ADR-001). Everything else comes through the domain's index.
 */
import {
  deleteEventSignup,
  type EventSignUpResponse,
  findEventSignUpsByEventId,
  updateEventSignUpById,
  type UpdateEventSignUpRequest,
} from "@/services/api"

/** The sign-ups of one event, or null where the api would not say. */
export async function listEventSignUps(eventId: number): Promise<EventSignUpResponse[] | null> {
  const {data, error} = await findEventSignUpsByEventId({path: {eventId}})
  if (error) return null
  return data ?? []
}

/** Board-side removal. [notify] is the board's choice to tell the person it happened. */
export async function removeSignUp(signUpId: number, notify: boolean): Promise<void> {
  await deleteEventSignup({path: {id: signUpId}, query: {notify}, throwOnError: true})
}

/** Board-side edit, addressed by the sign-up rather than by who is asking. */
export async function saveSignUpAsBoard(
  signUpId: number,
  body: UpdateEventSignUpRequest,
): Promise<EventSignUpResponse> {
  const {data} = await updateEventSignUpById({path: {id: signUpId}, body, throwOnError: true})
  return data!
}
