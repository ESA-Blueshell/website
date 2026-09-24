/**
 * The committee domain's public API: its own files import each other directly, and anything
 * outside it comes through here (frontend ADR-001). Re-exported by name rather than with
 * `export *`, because the list of names is the promise being made.
 */
export {
  deleteCommittee,
  listCommittees,
  listMyCommittees,
  loadCommitteePage,
  saveCommittee,
  saveGameOrganisers,
  saveNewCommittee,
  type Committee,
} from "./adapters/committees"
export {cellOf, driftItemOf, forgetCommittees, initialsOf, openingLineOf, reelItemOf, useCommittees} from "./useCommittees"
export {useCommitteeRights} from "./island/useCommitteeRights"
export type {
  CommitteeDetailResponse,
  CommitteeMemberRequest,
  CreateCommitteeRequest,
  UpdateCommitteeRequest,
} from "@/services/api"
