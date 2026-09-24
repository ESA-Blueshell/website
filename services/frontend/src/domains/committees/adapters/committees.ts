/**
 * Committee domain adapter — the only file in this domain that imports from `@/services/api`
 * (frontend ADR-002). Everything else comes through the door beside it, and every url a
 * committee's banner carries is resolved against the api here.
 */
import {
  apiUrl,
  archiveCommittee,
  type CommitteeDetailResponse,
  type CommitteePageResponse,
  createCommittee,
  type CreateCommitteeRequest,
  deleteCommitteeById,
  FileType,
  findCommitteePage,
  findCommittees,
  findCommitteesByUserId,
  type Image,
  setGameOrganisers,
  updateCommittee,
  updateCommitteePage,
  type UpdateCommitteeRequest,
  uploadCommitteeBanner,
  uploadPublicImage,
} from "@/services/api"
import type {Picture} from "@/components/island/pictures"
import type {Refused} from "@/types/api"
import {reasonFor} from "../refusals"

/** A committee as every reader gets it; only the board's answer also carries its members. */
export type Committee = Omit<CommitteeDetailResponse, "members"> & Partial<Pick<CommitteeDetailResponse, "members">>
export type CommitteePage = CommitteePageResponse

/** What the board writes about a committee: everything but its archived state. */
export interface CommitteeDraft {
  name: string
  slug: string
  listed: boolean
  description: string
  banner: string | null
  members: {userId: number; role: string | null}[]
  gameCodes: string[]
}

/** What a committee's own members write about it. */
export interface OwnPageDraft {
  description: string
  banner: string | null
  gameCodes: string[]
}

export interface CommitteeSaved {
  ok: true
  committee: Committee
}

const image = (one?: Image | null): Image | null =>
  one ? {...one, url: apiUrl(one.url), renditions: one.renditions.map(copy => ({...copy, url: apiUrl(copy.url)}))} : null

const withArt = <T extends {banner?: Image | null}>(committee: T): T => ({...committee, banner: image(committee.banner)})

/**
 * Every committee. Throws on a refusal rather than answering with an empty list: a list that
 * could not be read is not an association without committees, and the manager says so.
 */
export async function listCommittees(): Promise<CommitteeDetailResponse[]> {
  const res = await findCommittees({throwOnError: true})
  return ((res.data ?? []) as CommitteeDetailResponse[]).map(withArt)
}

/**
 * The committees the reader's own account belongs to, as distinct from all of them. Throws on a
 * refusal like the listing above it: a reader whose committees could not be read is not a reader
 * in none.
 */
export async function listMyCommittees(): Promise<CommitteeDetailResponse[]> {
  const res = await findCommitteesByUserId({throwOnError: true})
  return ((res.data ?? []) as CommitteeDetailResponse[]).map(withArt)
}

/** Every committee for the committees pages, or none where the api fails. */
export async function loadCommittees(): Promise<Committee[]> {
  const res = await findCommittees()
  return Array.isArray(res.data) ? (res.data as Committee[]).map(withArt) : []
}

/** One committee's page by its address, or null where no committee answers to it. */
export async function loadCommitteePage(address: string): Promise<CommitteePage | null> {
  const res = await findCommitteePage({path: {address}})
  return res.data ? withArt(res.data) : null
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

const boardBody = (draft: CommitteeDraft) => ({
  name: draft.name,
  slug: draft.slug || undefined,
  listed: draft.listed,
  description: draft.description,
  banner: draft.banner ?? undefined,
  members: draft.members.map(member => ({userId: member.userId, role: member.role ?? undefined})),
  gameCodes: draft.gameCodes,
})

export async function addCommittee(draft: CommitteeDraft): Promise<CommitteeSaved | Refused> {
  const res = await createCommittee({body: boardBody(draft)})
  if (res.error || !res.data) return {ok: false, reason: reasonFor(res.error, "The committee could not be added.")}
  return {ok: true, committee: withArt(res.data)}
}

export async function saveCommitteeAsBoard(id: number, version: number, draft: CommitteeDraft): Promise<CommitteeSaved | Refused> {
  const res = await updateCommittee({path: {id}, body: {...boardBody(draft), version}})
  if (res.error || !res.data) return {ok: false, reason: reasonFor(res.error, "The committee could not be saved.")}
  return {ok: true, committee: withArt(res.data)}
}

export async function saveOwnCommitteePage(id: number, draft: OwnPageDraft): Promise<CommitteeSaved | Refused> {
  const res = await updateCommitteePage({path: {id}, body: {...draft, banner: draft.banner ?? undefined}})
  if (res.error || !res.data) return {ok: false, reason: reasonFor(res.error, "The committee could not be saved.")}
  return {ok: true, committee: withArt(res.data)}
}

export async function setCommitteeArchived(id: number, archived: boolean): Promise<CommitteeSaved | Refused> {
  const res = await archiveCommittee({path: {id}, body: {archived}})
  if (res.error || !res.data) {
    return {ok: false, reason: reasonFor(res.error, archived ? "The committee could not be archived." : "The committee could not be brought back.")}
  }
  return {ok: true, committee: withArt(res.data)}
}

/**
 * Stores a banner somebody chose: through the committee's own route where it exists already, so
 * its members may, and as a public picture for one the board is still adding.
 */
export async function storeCommitteeBanner(file: File, committeeId: number | null): Promise<{ok: true; picture: Picture} | Refused> {
  const res = committeeId == null
    ? await uploadPublicImage({query: {type: FileType.COMMITTEE_BANNER}, body: {file}})
    : await uploadCommitteeBanner({path: {id: committeeId}, body: {file}})
  if (res.error || !res.data) return {ok: false, reason: reasonFor(res.error, "That picture could not be stored.")}
  return {ok: true, picture: image(res.data) as Picture}
}

/** Sets which committees organise events for a game, from the game's own form. */
export async function saveGameOrganisers(code: string, committeeIds: number[]): Promise<{ok: true} | Refused> {
  const res = await setGameOrganisers({path: {game: code}, body: {committeeIds}})
  if (res.error) return {ok: false, reason: reasonFor(res.error, "The committees could not be saved.")}
  return {ok: true}
}
