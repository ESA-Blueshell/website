/** The events waiting for the board, as Management's queue reads them. */
import {type QueuedEvent, listApprovalQueue} from "@/services/api"
import {readOr} from "@/utils/answers"

export type {QueuedEvent}

/** Every event waiting for the board, soonest first; null where the queue cannot be read. */
export const readApprovalQueue = (): Promise<QueuedEvent[] | null> => readOr(listApprovalQueue(), null)
