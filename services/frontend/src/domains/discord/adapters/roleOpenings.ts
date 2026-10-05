/** What a Discord role opens, as its page reads and sets it. Every change answers what the role opens now. */
import {
  type RoleAccess,
  type RoleOpeningState,
  archiveRoleChannel,
  createRoleChannel,
  listRoleOpenings,
  removeRoleOpening,
  setRoleOpening,
  unlinkDiscordRole,
} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import type {Saved} from "@/utils/refusals"
import {accepted, refusable} from "../refusals"

export type {RoleOpeningState}

type Openings = Promise<Saved<RoleOpeningState[]> | Refused>

/** What the role opens, or null where Discord cannot be read. */
export const readOpenings = (roleId: string): Promise<RoleOpeningState[] | null> => readOr(listRoleOpenings({path: {roleId}}), null)

export const openTo = (roleId: string, channelId: string, access: RoleAccess): Openings =>
  refusable(setRoleOpening({path: {roleId, channelId}, body: {access}}), "The access could not be set.")

export const closeTo = (roleId: string, channelId: string): Openings =>
  refusable(removeRoleOpening({path: {roleId, channelId}}), "The channel could not be taken off the role.")

export const createChannelFor = (roleId: string, name: string, category: string, access: RoleAccess): Openings =>
  refusable(createRoleChannel({path: {roleId}, body: {name, category, access}}), "The channel could not be made.")

export const archiveChannelOf = (roleId: string, channelId: string): Openings =>
  refusable(archiveRoleChannel({path: {roleId, channelId}}), "The channel could not be archived.")

/** Lets the role go from what it follows on the site. The role, its holders and its channels stay on Discord. */
export const unlinkRole = (roleId: string): Promise<{ok: true} | Refused> =>
  accepted(unlinkDiscordRole({path: {roleId}}), "The role could not be unlinked.")
