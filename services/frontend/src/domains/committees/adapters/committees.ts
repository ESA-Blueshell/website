/**
 * Committee domain adapter — the only file in this domain that imports from `@/services/api`
 * (frontend ADR-002). Everything else comes through the door beside it.
 */
import {
  type CommitteeDetailResponse,
  createCommittee,
  type CreateCommitteeRequest,
  deleteCommitteeById,
  findCommittees,
  findCommitteesByUserId,
  updateCommittee,
  type UpdateCommitteeRequest,
} from "@/services/api"

/**
 * Every committee. Throws on a refusal rather than answering with an empty list: a list that
 * could not be read is not an association without committees, and the manager says so.
 */
export async function listCommittees(): Promise<CommitteeDetailResponse[]> {
  const res = await findCommittees({throwOnError: true})
  return (res.data ?? []) as CommitteeDetailResponse[]
}

/**
 * The committees the reader's own account belongs to, as distinct from all of them. Throws on a
 * refusal like the listing above it: a reader whose committees could not be read is not a reader
 * in none.
 */
export async function listMyCommittees(): Promise<CommitteeDetailResponse[]> {
  const res = await findCommitteesByUserId({throwOnError: true})
  return (res.data ?? []) as CommitteeDetailResponse[]
}

/** Removes the committee, throwing on a refusal so the caller reports it rather than reading on. */
export async function deleteCommittee(id: number): Promise<void> {
  await deleteCommitteeById({path: {id}, throwOnError: true})
}

/** Records a new committee. Throws with the refusal the form reads its fields from. */
export async function saveNewCommittee(body: CreateCommitteeRequest): Promise<CommitteeDetailResponse> {
  const res = await createCommittee({body, throwOnError: true})
  return res.data!
}

/** Records a change to a committee. Throws with the refusal the form reads its fields from. */
export async function saveCommittee(
  id: number,
  body: UpdateCommitteeRequest,
): Promise<CommitteeDetailResponse> {
  const res = await updateCommittee({path: {id}, body, throwOnError: true})
  return res.data!
}
