/**
 * Discord roles adapter: the server's roles an event may ping, read through the api's bot. Null
 * where the api cannot ask Discord, which leaves the form showing what was chosen before.
 */
import {type DiscordRoleResponse, listDiscordRoles} from "@/services/api"

export async function listServerRoles(): Promise<DiscordRoleResponse[] | null> {
  const {data, error} = await listDiscordRoles()
  return error || !data ? null : data
}
