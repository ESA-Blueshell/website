/**
 * Starting and ending memberships in bulk. A membership belongs to the user domain rather than
 * to contribution: what one costs is a contribution's business, whether somebody holds one is
 * this domain's.
 *
 * Re-exported under the names the dialogs read them by, so a component names an intention
 * rather than an endpoint.
 */
import {
  apply,
  boardCreateMembership,
  type BoardCreateMembershipRequest,
  createMembership,
  deleteMembership,
  endMembership,
  endMemberships,
  findDeletedMemberships,
  findMemberships,
  type MembershipResponse,
  previewBulkEnd,
  previewBulkStart,
  reopenMembership,
  restoreMembership,
  type SignupOutcomeResponse,
  startMemberships,
  updateMembership,
  type UpdateMembershipRequest,
} from "@/services/api"
import {SIGNUP_TOKEN_HEADER} from "@/plugins/signupContinuation"

export const readMembershipStart = previewBulkStart
export const readMembershipEnd = previewBulkEnd
export const startTheMemberships = startMemberships
export const endTheMemberships = endMemberships

/** Every membership one account has held. Throws on a refusal. */
export async function listMembershipsFor(userId: number): Promise<MembershipResponse[]> {
  const res = await findMemberships({query: {userId}, throwOnError: true})
  return res.data ?? []
}

/** The memberships of one account that were deleted, which only an admin may read. Throws on a refusal. */
export async function listDeletedMembershipsFor(userId: number): Promise<MembershipResponse[]> {
  const res = await findDeletedMemberships({path: {userId}, throwOnError: true})
  return res.data ?? []
}

/** Ends the membership today. Throws on a refusal. */
export async function endOneMembership(id: number): Promise<void> {
  await endMembership({path: {id}, throwOnError: true})
}

/** Takes the end off a membership that was ended. Throws on a refusal. */
export async function reopenOneMembership(id: number): Promise<void> {
  await reopenMembership({path: {id}, throwOnError: true})
}

/** Removes the membership, inside the window it can still be put back in. Throws on a refusal. */
export async function deleteOneMembership(id: number): Promise<void> {
  await deleteMembership({path: {id}, throwOnError: true})
}

/** Puts a deleted membership back. Throws on a refusal. */
export async function restoreOneMembership(id: number): Promise<void> {
  await restoreMembership({path: {id}, throwOnError: true})
}

/**
 * Applies for membership as part of a signup, against the token the applicant holds. Answers
 * with what the application came to, which the form tells the applicant.
 */
export async function applyForMembership(
  token: string,
  conditionsAccepted: boolean,
): Promise<SignupOutcomeResponse> {
  const res = await apply({
    headers: {[SIGNUP_TOKEN_HEADER]: token},
    body: {conditionsAccepted},
    throwOnError: true,
  })
  return res.data!
}

/** Applies for membership as a signed-in account. Throws with the refusal the form reads. */
export async function startOwnMembership(conditionsAccepted: boolean): Promise<SignupOutcomeResponse> {
  const res = await createMembership({body: {conditionsAccepted}, throwOnError: true})
  return res.data!
}

/** Records a change to a membership. Throws with the refusal the form reads its fields from. */
export async function saveMembership(
  id: number,
  body: UpdateMembershipRequest,
): Promise<MembershipResponse> {
  const res = await updateMembership({path: {id}, body, throwOnError: true})
  return res.data!
}

/**
 * Starts a membership on somebody else's behalf, which is a board action rather than an
 * application. Throws with the refusal the form reads its fields from.
 */
export async function startMembershipAsBoard(
  userId: number,
  body: BoardCreateMembershipRequest,
): Promise<MembershipResponse> {
  const res = await boardCreateMembership({path: {userId}, body, throwOnError: true})
  return res.data!
}
