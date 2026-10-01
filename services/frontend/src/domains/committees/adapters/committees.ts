/**
 * Committee domain adapter — the only file in this domain that imports from `@/services/api`
 * (frontend ADR-002). Everything else comes through the door beside it, and every url a
 * committee's pictures carry is resolved against the api here.
 */
import {
  apiUrl,
  archiveCommittee,
  type DiscordPlace,
  type DiscordPlaceRequest,
  type CommitteeOwnPageRequest,
  type CommitteePageResponse,
  type CommitteeResponse,
  createCommittee,
  type CreateCommitteeRequest,
  deleteCommitteeById,
  FileType,
  findCommitteeDiscord,
  findCommitteePage,
  findCommittees,
  findCommitteesByUserId,
  type Image,
  setCommitteeDiscord,
  setGameOrganisers,
  updateCommittee,
  type UpdateCommitteeRequest,
  updateCommitteePage,
  uploadCommitteeBanner,
  uploadCommitteeIcon,
  uploadPublicImage,
} from "@/services/api"
import type {Picture} from "@/components/island/pictures"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"
import {readOr} from "@/utils/answers"
import {accepted, refusable} from "../refusals"

/** A committee as every reader gets it; its members only where the board or its own members read it. */
export type Committee = CommitteeResponse
export type CommitteePage = CommitteePageResponse

const image = (one?: Image | null): Image | null =>
  one ? {...one, url: apiUrl(one.url), renditions: one.renditions.map(copy => ({...copy, url: apiUrl(copy.url)}))} : null

const pictured = (stored: Saved<Image> | Refused): Saved<Picture> | Refused =>
  stored.ok ? {ok: true, saved: image(stored.saved) as Picture} : stored

const withArt = <T extends {banner?: Image | null; icon?: Image | null}>(committee: T): T =>
  ({...committee, banner: image(committee.banner), icon: image(committee.icon)})

/**
 * Every committee. Throws on a refusal rather than answering with an empty list: a list that
 * could not be read is not an association without committees, and the manager says so.
 */
export async function listCommittees(): Promise<Committee[]> {
  const res = await findCommittees({throwOnError: true})
  return (res.data ?? []).map(withArt)
}

/**
 * The committees the reader's own account belongs to, as distinct from all of them. Throws on a
 * refusal like the listing above it: a reader whose committees could not be read is not a reader
 * in none.
 */
export async function listMyCommittees(): Promise<Committee[]> {
  const res = await findCommitteesByUserId({throwOnError: true})
  return (res.data ?? []).map(withArt)
}

/** Every committee for the committees pages, or none where the api fails. */
export async function loadCommittees(): Promise<Committee[]> {
  const res = await findCommittees()
  return (res.data ?? []).map(withArt)
}

/** One committee's page by its address, or null where no committee answers to it. */
export async function loadCommitteePage(address: string): Promise<CommitteePage | null> {
  const res = await findCommitteePage({path: {address}})
  return res.data ? withArt(res.data) : null
}

/** Deletes the committee, or says why the api would not. */
export const removeCommittee = (id: number): Promise<{ok: true} | Refused> =>
  accepted(deleteCommitteeById({path: {id}}), "The committee could not be deleted.")

const withArtSaved = (saved: Saved<Committee> | Refused): Saved<Committee> | Refused =>
  saved.ok ? {ok: true, saved: withArt(saved.saved)} : saved

export const addCommittee = async (body: CreateCommitteeRequest): Promise<Saved<Committee> | Refused> =>
  withArtSaved(await refusable(createCommittee({body}), "The committee could not be added."))

export const saveCommitteeAsBoard = async (id: number, body: UpdateCommitteeRequest): Promise<Saved<Committee> | Refused> =>
  withArtSaved(await refusable(updateCommittee({path: {id}, body}), "The committee could not be saved."))

/** What a committee's own members write about it: its page, not its name, address or seats. */
export const saveOwnCommitteePage = async (id: number, body: CommitteeOwnPageRequest): Promise<Saved<Committee> | Refused> =>
  withArtSaved(await refusable(updateCommitteePage({path: {id}, body}), "The committee could not be saved."))

export const setCommitteeArchived = async (id: number, archived: boolean): Promise<Saved<Committee> | Refused> =>
  withArtSaved(await refusable(
    archiveCommittee({path: {id}, body: {archived}}),
    archived ? "The committee could not be archived." : "The committee could not be brought back.",
  ))

/**
 * Stores a banner somebody chose: through the committee's own route where it exists already, so
 * its members may, and as a public picture for one the board is still adding.
 */
export async function storeCommitteeBanner(file: File, committeeId: number | null): Promise<Saved<Picture> | Refused> {
  return pictured(await refusable(
    committeeId == null
      ? uploadPublicImage({query: {type: FileType.COMMITTEE_BANNER}, body: {file}})
      : uploadCommitteeBanner({path: {id: committeeId}, body: {file}}),
    "That picture could not be stored.",
  ))
}

/** Stores a logo somebody chose, the way [storeCommitteeBanner] stores a banner. */
export async function storeCommitteeIcon(file: File, committeeId: number | null): Promise<Saved<Picture> | Refused> {
  return pictured(await refusable(
    committeeId == null
      ? uploadPublicImage({query: {type: FileType.COMMITTEE_ICON}, body: {file}})
      : uploadCommitteeIcon({path: {id: committeeId}, body: {file}}),
    "That picture could not be stored.",
  ))
}

/** Sets which committees organise events for a game, from the game's own form. */
export const saveGameOrganisers = (code: string, committeeIds: number[]): Promise<{ok: true} | Refused> =>
  accepted(setGameOrganisers({path: {game: code}, body: {committeeIds}}), "The committees could not be saved.")

export type {DiscordPlace, DiscordPlaceRequest}

/** The role a committee's seats hold and the channels it opens; nothing where it could not be read. */
export const readCommitteeDiscord = (id: number): Promise<DiscordPlace | null> => readOr(findCommitteeDiscord({path: {id}}), null)

/** Links or makes the committee's role, and opens, closes or makes its channels. */
export const saveCommitteeDiscord = (id: number, body: DiscordPlaceRequest): Promise<Saved<DiscordPlace> | Refused> =>
  refusable(setCommitteeDiscord({path: {id}, body}), "Discord could not be set for the committee.")
