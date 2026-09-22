/**
 * Starting and ending memberships in bulk. A membership belongs to the user domain rather than
 * to contribution: what one costs is a contribution's business, whether somebody holds one is
 * this domain's.
 *
 * Re-exported under the names the dialogs read them by, so a component names an intention
 * rather than an endpoint.
 */
import {
  boardCreateMembership,
  type BoardCreateMembershipRequest,
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
  startMemberships,
} from "@/services/api"

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
