/** The Discord settings page's reads and writes: where the bots post and which roles the role-claim bot hands out. */
import {type DiscordBotSettings, findDiscordBotSettings, setDiscordBotSettings} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import {refusalReader, type Saved} from "@/utils/refusals"

const {refusable} = refusalReader({})

export type {DiscordBotSettings}

/** Where the bots post and which roles the role-claim bot hands out, or null where it could not be read. */
export const readBotSettings = (): Promise<DiscordBotSettings | null> => readOr(findDiscordBotSettings(), null)

export const saveBotSettings = (settings: DiscordBotSettings): Promise<Saved<DiscordBotSettings> | Refused> =>
  refusable(setDiscordBotSettings({body: settings}), "The settings could not be saved.")
