import {Role} from "@/services/api"

/**
 * The roles in seniority order, most senior first.
 *
 * The enum's own declaration order is ascending privilege for the chain that inherits, but it
 * also carries three values outside that chain, so the order a reader expects is written here
 * rather than read off the enum.
 */
const SENIORITY: Role[] = [
  Role.SYSTEM,
  Role.ADMIN,
  Role.TREASURER,
  Role.BOARD,
  Role.COMMITTEE,
  Role.MEMBER,
  Role.COMPANY,
  Role.VEGAN,
  Role.GUEST,
  Role.ANONYMOUS,
]

/**
 * The one role a listing shows for a person: the most senior they hold.
 *
 * A person holds a set, and a set has no last element — reading one gives a column that says
 * something different depending on what order the api happened to answer in.
 */
export function highestRole(roles: readonly Role[] | null | undefined): Role | null {
  if (!roles?.length) return null
  const held = new Set<string>(roles.map(role => `${role}`))
  return SENIORITY.find(role => held.has(`${role}`)) ?? null
}

/** The same role, as the user manager's column prints it. */
export function highestRoleLabel(roles: readonly Role[] | null | undefined): string {
  const role = highestRole(roles)
  return role ? `${role}`.toLocaleLowerCase() : ""
}
