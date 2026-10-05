/** What the bot may do on the server, read through the api. */
import {type BotGrant, type BotStandingResult, findBotStanding} from "@/services/api"
import {readOr} from "@/utils/answers"

export type {BotGrant}
export type BotStanding = BotStandingResult

/** The bot's standing, or nothing where it could not be read. A bot that is away reads as not connected. */
export const readBotStanding = (): Promise<BotStanding | null> => readOr(findBotStanding(), null)
