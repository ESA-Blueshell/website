/**
 * Discord roles adapter: the server's roles an event may ping, read through the api's bot. Null
 * where the api cannot ask Discord, which leaves the form showing what was chosen before.
 *
 * Canvas only: the sample api answers this without a list, so a handful of roles stand in.
 */
import {type DiscordRoleResponse, listDiscordRoles} from "@/services/api"

const SAMPLE_ROLES: DiscordRoleResponse[] = [
  {id: "9001", name: "events"},
  {id: "9002", name: "valorant"},
  {id: "9003", name: "league-of-legends"},
  {id: "9004", name: "chess"},
  {id: "9005", name: "members"},
  {id: "9006", name: "board"},
]

export async function listServerRoles(): Promise<DiscordRoleResponse[] | null> {
  const {data, error} = await listDiscordRoles()
  if (error) return null
  return Array.isArray(data) ? data : SAMPLE_ROLES
}
