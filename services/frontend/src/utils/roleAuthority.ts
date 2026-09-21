import {Role} from "@/services/api"

/**
 * Mirrors the chain on the api's `Role` enum (`shared/enums/Role.kt`); change one side and change
 * the other, or a page offers actions the api refuses.
 */
function inheritedBy(role: Role): Role | undefined {
  switch (role) {
    case Role.GUEST: return Role.ANONYMOUS
    case Role.MEMBER: return Role.GUEST
    case Role.COMMITTEE: return Role.MEMBER
    case Role.BOARD: return Role.COMMITTEE
    case Role.TREASURER: return Role.BOARD
    case Role.ADMIN: return Role.TREASURER
    case Role.SYSTEM: return Role.ADMIN
    default: return undefined
  }
}

function reaches(held: Role, wanted: Role): boolean {
  let current: Role | undefined = held
  while (current) {
    if (current === wanted) return true
    current = inheritedBy(current)
  }
  return false
}

/**
 * Whether the roles held reach the role asked for by inheritance rather than by a literal match:
 * an admin is a board member without carrying the board role.
 */
export function hasAuthority(held: Role[] | undefined | null, wanted: Role): boolean {
  return (held ?? []).some((role) => reaches(role, wanted))
}
