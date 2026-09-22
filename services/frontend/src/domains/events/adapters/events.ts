/**
 * Event adapter: the reads and writes of the events themselves. Like every adapter in this
 * domain it is one of the only files here that reaches the generated client (frontend ADR-001).
 */
import {
  apiUrl,
  approveEvent,
  createEvent,
  type CreateEventRequest,
  deleteEventById,
  downloadEventBanner,
  type EventResponse,
  findEventById,
  findEvents,
  type FindEventsData,
  type PageMetadata,
  updateEvent,
  type UpdateEventRequest,
  uploadEventBanner,
} from "@/services/api"

/** Which events a caller is asking for: a window, a page of one, and the order to read them in. */
export type EventQuery = NonNullable<FindEventsData["query"]>

/** A page of events, and what the api said about the page it answered with. */
export type EventPage = {events: EventResponse[]; page?: PageMetadata}

/** The event behind the number. Throws on a refusal, so the caller reports it rather than reading on. */
export async function readEvent(id: number): Promise<EventResponse> {
  const {data} = await findEventById({path: {id}, throwOnError: true})
  return data!
}

/**
 * The events the query names. Throws on a refusal rather than answering with an empty listing:
 * a listing that could not be read is not an association with nothing on.
 */
export async function listEvents(query: EventQuery = {}): Promise<EventResponse[]> {
  const {data} = await findEvents({query, throwOnError: true})
  return data?.content ?? []
}

/**
 * One page of events, with the api's own count of the rest. The pager needs both, and a failed
 * read leaves the pane empty rather than crashing the page around it, so this one does not throw.
 */
export async function readEventPage(query: EventQuery): Promise<EventPage> {
  const {data} = await findEvents({query})
  return {events: data?.content ?? [], page: data?.page}
}

/** Records a new event. Throws with the refusal the form reads its fields from. */
export async function saveNewEvent(body: CreateEventRequest): Promise<EventResponse> {
  const {data} = await createEvent({body, throwOnError: true})
  return data!
}

/** Records a change to an event. Throws with the refusal the form reads its fields from. */
export async function saveEvent(id: number, body: UpdateEventRequest): Promise<EventResponse> {
  const {data} = await updateEvent({path: {id}, body, throwOnError: true})
  return data!
}

/** Approves the event, or takes the approval off it. Throws on a refusal. */
export async function setEventApproved(id: number, approved: boolean): Promise<EventResponse> {
  const {data} = await approveEvent({path: {id}, query: {approved}, throwOnError: true})
  return data!
}

/** Removes the event. Throws on a refusal, so the card says the removal did not happen. */
export async function deleteEvent(eventId: number): Promise<void> {
  await deleteEventById({path: {eventId}, throwOnError: true})
}

/** The banner on an event, as the bytes a browser can draw. Throws on a refusal. */
export async function readEventBanner(eventId: number): Promise<Blob> {
  const {data} = await downloadEventBanner({
    path: {eventId},
    throwOnError: true,
    responseType: "blob",
  })
  return data as Blob
}

/** Stores a banner and answers with the file it was stored as. Throws on a refusal. */
export async function saveEventBanner(file: File): Promise<{id: number}> {
  const {data} = await uploadEventBanner({body: {file}, throwOnError: true})
  return {id: data!.id!}
}

/**
 * The address a file on an event is served from. A picture is stored as a path, and the card
 * draws it from wherever the api lives rather than from wherever the page was loaded.
 */
export function eventFileUrl(path: string): string {
  return apiUrl(path)
}
