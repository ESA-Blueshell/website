/** The Discord settings page's reads and writes: each server-wide cohort's role and channels, and where the bots post. */
import {
  type DiscordBotSettings,
  type DiscordPlace,
  type DiscordPlaceRequest,
  type ServerCohortRole,
  findDiscordBotSettings,
  findServerCohortDiscord,
  listServerCohortRoles,
  setDiscordBotSettings,
  setServerCohortDiscord,
} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import type {Saved} from "@/utils/refusals"
import {reasonFor, refusable} from "../refusals"

export type {DiscordBotSettings, ServerCohortRole}

/** The server-wide cohorts and their roles, or null where they could not be read. */
export const readServerCohortRoles = (): Promise<ServerCohortRole[] | null> => readOr(listServerCohortRoles(), null)

/** A server-wide cohort's role and the channels it opens, or null where Discord could not be read. */
export const readCohortDiscord = (key: string): Promise<DiscordPlace | null> => readOr(findServerCohortDiscord({path: {key}}), null)

/** A refusal that names the cohort following the role now, where that is why it was refused. */
export type CohortDiscordRefused = Refused & {linkedTo: string | null}

/** Links or creates a server-wide cohort's role and sets the channels it opens. */
export const saveCohortDiscord = async (key: string, choice: DiscordPlaceRequest): Promise<Saved<DiscordPlace> | CohortDiscordRefused> => {
  const res = await setServerCohortDiscord({path: {key}, body: choice})
  if (!res.error && res.data != null) return {ok: true, saved: res.data}
  const body = res.error as {code?: string; cohort?: string} | undefined
  return {ok: false, reason: reasonFor(res.error, "Discord could not be set."), linkedTo: body?.code === "TargetLinkedElsewhere" ? (body.cohort ?? null) : null}
}

/** Where the bots post and which roles the role-claim bot hands out, or null where it could not be read. */
export const readBotSettings = (): Promise<DiscordBotSettings | null> => readOr(findDiscordBotSettings(), null)

export const saveBotSettings = (settings: DiscordBotSettings): Promise<Saved<DiscordBotSettings> | Refused> =>
  refusable(setDiscordBotSettings({body: settings}), "The settings could not be saved.")
