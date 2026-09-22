/**
 * Event adapter: the reads and writes of the events themselves. Like every adapter in this
 * domain it is one of the only files here that reaches the generated client (frontend ADR-001).
 */
import {type EventResponse, findEventById} from "@/services/api"

/** The event behind the number. Throws on a refusal, so the caller reports it rather than reading on. */
export async function readEvent(id: number): Promise<EventResponse> {
  const {data} = await findEventById({path: {id}, throwOnError: true})
  return data!
}
