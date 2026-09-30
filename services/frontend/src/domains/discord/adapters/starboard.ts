/**
 * Starboard adapter: the messages the server starred, which the api reads through the bot. Where
 * the bot is not set up or Discord has not answered the api says 503, and this answers null.
 */
import {readStarboard as readStarboardEntries, type StarboardEntryResponse} from "@/services/api"
import {readOr} from "@/utils/answers"

export const readStarboard = (): Promise<StarboardEntryResponse[] | null> =>
  readOr(readStarboardEntries(), null)
