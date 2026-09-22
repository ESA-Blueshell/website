/**
 * The committee domain's public API: its own files import each other directly, and anything
 * outside it comes through here (frontend ADR-001). Re-exported by name rather than with
 * `export *`, because the list of names is the promise being made.
 */
export {
  deleteCommittee,
  listCommittees,
  listMyCommittees,
  saveCommittee,
  saveNewCommittee,
} from "./adapters/committees"
export type {
  CommitteeDetailResponse,
  CommitteeMemberRequest,
  CreateCommitteeRequest,
  UpdateCommitteeRequest,
} from "@/services/api"
