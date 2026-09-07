/**
 * Committee domain adapter — the only file in this domain that imports from `@/services/api`
 * (frontend ADR-002). Everything else comes through the door beside it.
 */
import {type CommitteeDetailResponse, deleteCommitteeById, findCommittees} from "@/services/api"

/**
 * Every committee. Throws on a refusal rather than answering with an empty list: a list that
 * could not be read is not an association without committees, and the manager says so.
 */
export async function listCommittees(): Promise<CommitteeDetailResponse[]> {
  const res = await findCommittees({throwOnError: true})
  return (res.data ?? []) as CommitteeDetailResponse[]
}

/** Removes the committee, throwing on a refusal so the caller reports it rather than reading on. */
export async function deleteCommittee(id: number): Promise<void> {
  await deleteCommitteeById({path: {id}, throwOnError: true})
}
