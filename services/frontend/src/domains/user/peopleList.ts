import {type CommitteeResponse, MemberType, type MembershipResponse, Role, type UserDetailResponse} from "@/services/api"
import {highestRoleLabel} from "./roles"

export type MembershipState = "current" | "pending" | "former" | "never"

/** Why a person needs a look: each is something a board member can go and fix. */
export type NeedsLook = "locked" | "role-waiting" | "no-discord" | "no-address"

export const NEEDS_LOOK_WORDS: Record<NeedsLook, string> = {
  "locked": "Locked",
  "role-waiting": "Role waits on two-factor",
  "no-discord": "No Discord linked",
  "no-address": "No address",
}

export const MEMBERSHIP_WORDS: Record<MembershipState, string> = {
  current: "Member",
  pending: "Pending member",
  former: "Former member",
  never: "Never a member",
}

/** Where a person stands: a running membership is pending until its first contribution is paid. */
export function membershipStateOf(own: Pick<MembershipResponse, "endDate" | "pending">[]): MembershipState {
  const running = own.filter((one) => !one.endDate)
  if (running.some((one) => !one.pending)) return "current"
  if (running.length > 0) return "pending"
  return own.length === 0 ? "never" : "former"
}

/** One person as the Users list draws, filters and sorts them. */
export interface PersonRow {
  id: number
  fullName: string
  username: string
  email: string
  role: string
  membership: MembershipState
  /** The type of their latest membership, or null for someone never a member. */
  type: MemberType | null
  memberSince: string | null
  needs: NeedsLook[]
  /** Every searchable field, folded the way a search is. */
  haystack: string
}

export interface PeopleFilter {
  search: string
  membership: MembershipState | null
  type: MemberType | null
  /** A reason, or "any" for anybody with a reason at all. */
  needs: NeedsLook | "any" | null
}

export type PeopleSortKey = "name" | "membership" | "memberSince"

const GRANTED = new Set<Role>([Role.BOARD, Role.TREASURER, Role.ADMIN])

/** Lower case, accents gone, punctuation read as a space: typing zoe finds Zoë. */
export const fold = (text: string): string =>
  text.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase().replace(/[^\p{L}\p{N}]+/gu, " ").trim()

/** Why an account needs a look, each reason something a board member can fix. */
export function needsLookOf(user: UserDetailResponse): NeedsLook[] {
  const needs: NeedsLook[] = []
  if (user.locked) needs.push("locked")
  if (!user.twoFactorOn && user.roles.some((role) => GRANTED.has(role))) needs.push("role-waiting")
  if (!user.discordId) needs.push("no-discord")
  if (user.addressId == null) needs.push("no-address")
  return needs
}

/** The rows the list draws, with each person's memberships and committees folded into their search. No address is: a list opens none. */
export function peopleRows(
  users: UserDetailResponse[],
  memberships: MembershipResponse[],
  committees: CommitteeResponse[],
): PersonRow[] {
  const held = new Map<number, MembershipResponse[]>()
  for (const one of memberships) held.set(one.userId, [...(held.get(one.userId) ?? []), one])
  const committeesOf = new Map<number, string[]>()
  for (const committee of committees) {
    for (const seat of committee.members ?? []) committeesOf.set(seat.userId, [...(committeesOf.get(seat.userId) ?? []), committee.name])
  }

  return users.map((user) => {
    const own = held.get(user.id) ?? []
    const latest = own.reduce<MembershipResponse | null>((newest, one) => (!newest || one.startDate > newest.startDate ? one : newest), null)
    const membership = membershipStateOf(own)
    const type = latest?.memberType ?? null
    return {
      id: user.id,
      fullName: user.fullName,
      username: user.username,
      email: user.email,
      role: highestRoleLabel(user.roles),
      membership,
      type,
      memberSince: own.length === 0 ? null : own.map((one) => one.startDate).sort()[0]!,
      needs: needsLookOf(user),
      haystack: fold([
        user.fullName, user.firstName, user.lastName, user.username, user.email, user.discord ?? "", user.phoneNumber ?? "",
        ...(committeesOf.get(user.id) ?? []), ...user.roles, MEMBERSHIP_WORDS[membership], type ?? "",
      ].join(" ")),
    }
  })
}

/** The rows matching every word of the search and every filter set. */
export function filterPeople(rows: PersonRow[], filter: PeopleFilter): PersonRow[] {
  const words = fold(filter.search).split(" ").filter(Boolean)
  return rows.filter((row) =>
    (filter.membership === null || row.membership === filter.membership)
    && (filter.type === null || row.type === filter.type)
    && (filter.needs === null || (filter.needs === "any" ? row.needs.length > 0 : row.needs.includes(filter.needs)))
    && words.every((word) => row.haystack.includes(word)))
}

const MEMBERSHIP_ORDER: Record<MembershipState, number> = {current: 0, pending: 1, former: 2, never: 3}

/** Sorted by one column; people never a member sort after everyone else by date. */
export function sortPeople(rows: PersonRow[], key: PeopleSortKey, descending: boolean): PersonRow[] {
  const compare = (a: PersonRow, b: PersonRow): number => {
    if (key === "membership") return MEMBERSHIP_ORDER[a.membership] - MEMBERSHIP_ORDER[b.membership]
    if (key === "memberSince") return (a.memberSince ?? "9999").localeCompare(b.memberSince ?? "9999")
    return a.fullName.localeCompare(b.fullName)
  }
  const sorted = [...rows].sort(compare)
  return descending ? sorted.reverse() : sorted
}
