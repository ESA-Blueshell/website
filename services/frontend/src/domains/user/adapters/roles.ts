/**
 * What a person may reach, read and written. Imports from @/services/api because it is an
 * adapter (frontend ADR-002); everything else in the domain comes through here.
 */
import {
  findUserRoleChanges,
  findUserRoles,
  type Role,
  type RoleChangeResponse,
  setUserRoles,
  type UserRolesResponse,
} from "@/services/api"
import {reasonFor} from "../refusals"

export type RoleStanding = UserRolesResponse
export type RoleChange = RoleChangeResponse

export type SaveRolesResult =
  | {ok: true; standing: RoleStanding}
  | {ok: false; reason: string}

export async function readRoleStanding(userId: number): Promise<RoleStanding | null> {
  const res = await findUserRoles({path: {userId}})
  if (res.error || !res.data) return null
  return res.data
}

export async function listRoleChanges(userId: number): Promise<RoleChange[] | null> {
  const res = await findUserRoleChanges({path: {userId}})
  if (res.error || !res.data) return null
  return res.data
}

/**
 * Sets the whole granted set. The request states the end state, so saving the same thing twice
 * is safe and two admins editing one person cannot toggle past each other.
 */
export async function saveRolesOrReason(
  userId: number,
  roles: Role[],
  note: string | null,
): Promise<SaveRolesResult> {
  const res = await setUserRoles({path: {userId}, body: {roles, note}})
  if (res.error || !res.data) {
    return {ok: false, reason: reasonFor(res.error, "Those roles could not be saved.")}
  }
  return {ok: true, standing: res.data}
}
