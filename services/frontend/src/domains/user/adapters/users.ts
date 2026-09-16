/**
 * User domain adapter — the only file in this domain that imports from @/services/api
 * (per frontend ADR-002). Everything else imports from here.
 */
import {
  type AddressResponse,
  deleteUserById,
  findAllAddresses,
  findDeletedUsers,
  findMemberships,
  findUserById,
  findUsers,
  type MembershipResponse,
  type UserDetailResponse,
} from "@/services/api"

/**
 * An account here, as the thing attaching one needs to name it: who it belongs to, and how to
 * tell two of them apart. Named for the account rather than for the member, because what
 * attaches one is a board membership or a roster entry, which is a member of its own.
 */
export interface MemberAccount {
  id: number
  name: string
  email: string | null
}

/**
 * The accounts something can be attached to, or nothing at all where the api would not say.
 *
 * Asked for once and filtered where it is used, the way the rest of the site's member pickers
 * work. Attaching a roster entry or a board membership is rare enough that a search round trip per
 * keystroke would buy nothing.
 *
 * A list that could not be read is not an empty one, and a picker has to tell them apart: read as
 * emptiness, a refused request tells a board member that nobody here has an account.
 */
export async function loadMemberAccounts(): Promise<MemberAccount[] | null> {
  // No size: this wants the whole listing, and a size that named a bound never gave one — it
  // was answered with everybody anyway (#1145). Saying so beats a number that did nothing.
  const res = await findUsers({})
  if (res.error || !res.data?.content) return null
  return res.data.content
    .filter(user => user.id != null)
    .map(user => ({
      id: user.id as number,
      name: user.fullName ?? user.email ?? `Member ${user.id}`,
      email: user.email ?? null,
    }))
    .sort((a, b) => a.name.localeCompare(b.name))
}

/**
 * The accounts whose name, username or Discord handle carries what somebody typed, one page of
 * them. A picker cannot hold the whole table, so it asks as the reader types (#1139).
 */
export async function searchMemberAccounts(term: string, size: number): Promise<UserDetailResponse[]> {
  const res = await findUsers({query: {search: term, page: 0, size}})
  return res.data?.content ?? []
}

/**
 * The whole listing, as the manager pages read it: they filter and page in the browser, and no
 * size is named because one never bounded anything (#1145).
 *
 * Throws on a refusal rather than answering with an empty list, so a page reports it instead of
 * telling a board member the association has nobody in it.
 */
export async function listUsers(): Promise<UserDetailResponse[]> {
  const res = await findUsers({throwOnError: true})
  return res.data?.content ?? []
}

/** One account in full, or nothing where the api would not say. */
export async function readUser(userId: number): Promise<UserDetailResponse | null> {
  const res = await findUserById({path: {userId}})
  return res.data ?? null
}

/** Accounts that were deleted and are still inside their restore window. */
export async function listDeletedUsers(): Promise<UserDetailResponse[]> {
  const res = await findDeletedUsers({throwOnError: true})
  return res.data?.content ?? []
}

export async function listMemberships(): Promise<MembershipResponse[]> {
  const res = await findMemberships({throwOnError: true})
  return res.data ?? []
}

/** Deletes the account, throwing on a refusal so the caller reports it rather than reading on. */
export async function deleteUser(userId: number): Promise<void> {
  await deleteUserById({path: {userId}, throwOnError: true})
}

/** Every address on file, which the address manager pairs with the accounts above. */
export async function listAddresses(): Promise<AddressResponse[]> {
  const res = await findAllAddresses({throwOnError: true})
  return res.data ?? []
}
