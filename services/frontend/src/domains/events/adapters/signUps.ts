/**
 * Event sign-up adapter: one of the few files in this domain that reaches the generated client
 * (frontend ADR-001). Everything else comes through the domain's index.
 */
import {
  createEventSignup,
  type CreateEventSignUpRequest,
  deleteEventSignup,
  type EventSignUpResponse,
  findEventSignUps,
  findEventSignUpsByAccessToken,
  findEventSignUpsByEventId,
  updateEventSignUp,
  updateEventSignUpById,
  type UpdateEventSignUpRequest,
} from "@/services/api"

/** The header a guest is known by, having no account to be known by instead. */
const GUEST_ACCESS_HEADER = "X-Guest-Access-Token"

/** A sign-up as it was written down, and the token a guest is to be remembered by. */
export type SavedSignUp = {signUp: EventSignUpResponse; guestAccessToken: string | null}

/** The sign-ups of one event, or null where the api would not say. */
export async function listEventSignUps(eventId: number): Promise<EventSignUpResponse[] | null> {
  const {data, error} = await findEventSignUpsByEventId({path: {eventId}})
  if (error) return null
  return data ?? []
}

/** What one account has signed up for, from the moment named. Throws on a refusal. */
export async function listOwnSignUps(userId: number, from: string): Promise<EventSignUpResponse[]> {
  const {data} = await findEventSignUps({query: {from, userId}, throwOnError: true})
  return data ?? []
}

/**
 * What the guest holding this token has signed up for. Throws on a refusal, which the page tells
 * apart from a guest who has signed up for nothing.
 */
export async function listSignUpsByAccessToken(token: string): Promise<EventSignUpResponse[]> {
  const {data} = await findEventSignUpsByAccessToken({
    headers: {[GUEST_ACCESS_HEADER]: token},
    throwOnError: true,
  })
  return data ?? []
}

/** Signs the reader up for the event. Throws with the refusal the form reads its fields from. */
export async function signUpForEvent(
  eventId: number,
  body: CreateEventSignUpRequest,
): Promise<SavedSignUp> {
  const res = await createEventSignup({path: {eventId}, body, throwOnError: true})
  return {signUp: res.data!, guestAccessToken: guestTokenIn(res.headers)}
}

/**
 * Changes the reader's own sign-up. A guest carries the token they were given, because nothing
 * else says the sign-up is theirs.
 */
export async function changeOwnSignUp(
  eventId: number,
  body: UpdateEventSignUpRequest,
  guestAccessToken: string | null = null,
): Promise<SavedSignUp> {
  const res = await updateEventSignUp({
    path: {eventId},
    headers: guestAccessToken ? {[GUEST_ACCESS_HEADER]: guestAccessToken} : undefined,
    body,
    throwOnError: true,
  })
  return {signUp: res.data!, guestAccessToken: guestTokenIn(res.headers) ?? guestAccessToken}
}

/** Withdraws the reader's own sign-up, as a guest with their token or as an account. */
export async function withdrawSignUp(
  signUpId: number,
  guestAccessToken: string | null = null,
): Promise<void> {
  await deleteEventSignup({
    path: {id: signUpId},
    headers: guestAccessToken ? {[GUEST_ACCESS_HEADER]: guestAccessToken} : undefined,
    throwOnError: true,
  })
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

/** The token a guest is given back, whichever casing the header came in. */
function guestTokenIn(headers: unknown): string | null {
  if (headers == null || typeof headers !== "object") return null
  const values = headers as Record<string, string | string[] | undefined>
  const raw = values["x-guest-access-token"] ?? values[GUEST_ACCESS_HEADER]
  if (typeof raw === "string") return raw
  if (Array.isArray(raw) && raw.length > 0 && raw[0] != null) return raw[0]
  return null
}
