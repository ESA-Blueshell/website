import {Role} from "@/services/api"

/**
 * What each role inherits, mirroring the chain declared on the api's `Role` enum
 * (`shared/enums/Role.kt`). Change one side and change the other: the chain decides what the api
 * permits, and a page that reads it differently offers actions the api refuses, or hides actions
 * it would allow.
 */
const INHERITS: Partial<Record<Role, Role>> = {
  [Role.GUEST]: Role.ANONYMOUS,
  [Role.MEMBER]: Role.GUEST,
  [Role.COMMITTEE]: Role.MEMBER,
  [Role.BOARD]: Role.COMMITTEE,
  [Role.TREASURER]: Role.BOARD,
  [Role.ADMIN]: Role.TREASURER,
  [Role.SYSTEM]: Role.ADMIN,
}

function reaches(held: Role, wanted: Role): boolean {
  let current: Role | undefined = held
  while (current) {
    if (current === wanted) return true
    current = INHERITS[current]
  }
  return false
}

/**
 * Whether the roles held reach the role asked for, by inheritance rather than by a literal match:
 * an admin is a board member without carrying the board role.
 */
export function hasAuthority(held: Role[] | undefined | null, wanted: Role): boolean {
  return (held ?? []).some((role) => reaches(role, wanted))
}
