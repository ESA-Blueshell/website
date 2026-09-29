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
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import type {Saved} from "@/utils/refusals"
import {refusable} from "../refusals"

export type RoleStanding = UserRolesResponse
export type RoleChange = RoleChangeResponse

export type SaveRolesResult = Saved<RoleStanding> | Refused

export const readRoleStanding = (userId: number): Promise<RoleStanding | null> =>
  readOr(findUserRoles({path: {userId}}), null)

export const listRoleChanges = (userId: number): Promise<RoleChange[] | null> =>
  readOr(findUserRoleChanges({path: {userId}}), null)

/**
 * Sets the whole granted set. The request states the end state, so saving the same thing twice
 * is safe and two admins editing one person cannot toggle past each other.
 */
export const saveRolesOrReason = (userId: number, roles: Role[], note: string | null): Promise<SaveRolesResult> =>
  refusable(setUserRoles({path: {userId}, body: {roles, note}}), "Those roles could not be saved.")
