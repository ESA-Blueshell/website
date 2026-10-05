/** The Discord settings page's reads and writes: the role each server-wide cohort follows, and where the bots post. */
import {
  type DiscordBotSettings,
  type ServerCohortRole,
  findDiscordBotSettings,
  listServerCohortRoles,
  setDiscordBotSettings,
  setServerCohortRole,
} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import type {Saved} from "@/utils/refusals"
import {refusable} from "../refusals"

export type {DiscordBotSettings, ServerCohortRole}

/** The server-wide cohorts and their roles, or null where they could not be read. */
export const readServerCohortRoles = (): Promise<ServerCohortRole[] | null> => readOr(listServerCohortRoles(), null)

/** Points a server-wide cohort at an existing role, or at a new one named after it. */
export const setCohortRole = (key: string, choice: {roleId: string} | {create: true}): Promise<Saved<ServerCohortRole[]> | Refused> =>
  refusable(setServerCohortRole({path: {key}, body: "roleId" in choice ? {roleId: choice.roleId, create: false} : {create: true}}), "The role could not be set.")

/** Where the bots post and which roles the role-claim bot hands out, or null where it could not be read. */
export const readBotSettings = (): Promise<DiscordBotSettings | null> => readOr(findDiscordBotSettings(), null)

export const saveBotSettings = (settings: DiscordBotSettings): Promise<Saved<DiscordBotSettings> | Refused> =>
  refusable(setDiscordBotSettings({body: settings}), "The settings could not be saved.")
