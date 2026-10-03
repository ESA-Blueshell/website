/**
 * User domain adapter — the only file in this domain that imports from @/services/api
 * (per frontend ADR-002). Everything else imports from here.
 */
import {
  type AddressResponse,
  createAddress,
  type CreateAddressRequest,
  createUser,
  type CreateUserRequest,
  deleteAddressById,
  deleteUserById,
  findAddressById,
  findDeletedUsers,
  findMemberships,
  findUserById,
  findMemberProfileByUserId,
  setNameOnRosters,
  findUsers,
  type MemberProfileResponse,
  type MembershipResponse,
  saveAddress,
  type SignupAddressRequest,
  signUp,
  type SignupDetailsRequest,
  type SignupSessionResponse,
  updateAddress,
  type UpdateAddressRequest,
  updateDetails,
  updateUser,
  type UpdateUserRequest,
  type UserDetailResponse,
} from "@/services/api"
import {SIGNUP_TOKEN_HEADER} from "@/plugins/signupContinuation"
import {readOr} from "@/utils/answers"

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
  const page = await readOr(findUsers({}), null)
  if (!page?.content) return null
  return page.content
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
  return (await findMemberAccounts(term, size)) ?? []
}

/** The same search, answering nothing where the api refused it rather than an empty page. */
export async function findMemberAccounts(term: string, size: number): Promise<UserDetailResponse[] | null> {
  const page = await readOr(findUsers({query: {search: term, page: 0, size}}), null)
  return page ? page.content ?? [] : null
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

/** Removes the address, throwing on a refusal so the caller reports it rather than reading on. */
export async function deleteAddress(id: number): Promise<void> {
  await deleteAddressById({path: {id}, throwOnError: true})
}

/** One address in full. Throws on a refusal, so the form reports it rather than showing a blank. */
export async function readAddress(id: number): Promise<AddressResponse> {
  const res = await findAddressById({path: {id}, throwOnError: true})
  return res.data!
}

/** Records a new address. Throws with the refusal the form reads its fields from. */
export async function saveNewAddress(body: CreateAddressRequest): Promise<AddressResponse> {
  const res = await createAddress({body, throwOnError: true})
  return res.data!
}

/** Records a change to an address. Throws with the refusal the form reads its fields from. */
export async function saveAddressChange(
  id: number,
  body: UpdateAddressRequest,
): Promise<AddressResponse> {
  const res = await updateAddress({path: {id}, body, throwOnError: true})
  return res.data!
}

/**
 * Records the address of a signup in progress, against the token the applicant holds rather
 * than against an account they cannot sign in to yet.
 */
export async function saveSignupAddress(
  token: string,
  body: SignupAddressRequest,
): Promise<void> {
  await saveAddress({
    headers: {[SIGNUP_TOKEN_HEADER]: token},
    body,
    throwOnError: true,
  })
}

/** Records a new account. Throws with the refusal the form reads its fields from. */
export async function saveNewUser(body: CreateUserRequest): Promise<UserDetailResponse> {
  const res = await createUser({body, throwOnError: true})
  return res.data!
}

/** Records a change to an account. Throws with the refusal the form reads its fields from. */
export async function saveUser(id: number, body: UpdateUserRequest): Promise<UserDetailResponse> {
  const res = await updateUser({path: {id}, body, throwOnError: true})
  return res.data!
}

/**
 * Starts a signup from what the applicant typed, and answers with the session the rest of the
 * signup is carried out against: nothing authorises them to read the account back yet.
 */
export async function startSignup(body: CreateUserRequest): Promise<SignupSessionResponse> {
  const res = await signUp({body, throwOnError: true})
  return res.data!
}

/** Records the details of a signup in progress, against the token the applicant holds. */
export async function saveSignupDetails(
  token: string,
  body: SignupDetailsRequest,
): Promise<void> {
  await updateDetails({
    headers: {[SIGNUP_TOKEN_HEADER]: token},
    body,
    throwOnError: true,
  })
}

/** Says whether the esports pages print this person's name beside their handle; answers what is now stored, or nothing where it was refused. */
export async function saveNameOnRosters(userId: number, shown: boolean): Promise<boolean | null> {
  const res = await setNameOnRosters({path: {userId}, body: {shown}})
  return res.data?.nameOnRosters ?? null
}

/** The member profile on an account, or nothing where there is none to read. */
export async function readMemberProfile(userId: number): Promise<MemberProfileResponse | null> {
  const res = await findMemberProfileByUserId({path: {userId}})
  return res.data ?? null
}
