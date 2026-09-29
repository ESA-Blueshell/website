/**
 * Discord roles adapter: the server's roles an event may ping, read through the api's bot. Null
 * where the api cannot ask Discord, which leaves the form showing what was chosen before.
 */
import {type DiscordRoleResponse, listDiscordRoles} from "@/services/api"
import {readOr} from "@/utils/answers"

export const listServerRoles = (): Promise<DiscordRoleResponse[] | null> =>
  readOr(listDiscordRoles(), null)
