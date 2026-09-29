/**
 * Board domain adapter: the only file in this domain that imports from @/services/api
 * (per frontend ADR-002). Everything else imports from here.
 */
import {
  addMember,
  apiUrl,
  createBoard,
  deleteBoard,
  FileType,
  findAllBoards,
  linkMember,
  removeMember,
  updateBoard,
  updateMember,
  uploadPublicImage,
} from "@/services/api"
import type {
  AddBoardMemberRequest,
  BoardMemberResponse,
  BoardRequest,
  BoardResponse,
  Image,
  UpdateBoardMemberRequest,
} from "@/services/api"
import type {PictureStore} from "@/components/island/pictures"
import {accepted, refusable} from "../refusals"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"

// Re-exported so this adapter still answers for its own surface, while the type has one definition.
export type {Refused}

export type Board = BoardResponse
export type BoardMember = BoardMemberResponse

/**
 * The pictures the api points at, resolved to where they are actually served.
 *
 * Done here rather than at each place one is drawn: the api answers with its own paths, and a
 * bare path resolves against the frontend's origin instead of the api's. Every width is
 * resolved, not only the full-size one, so a component can hand the whole set to a `srcset`
 * without checking which of them are usable.
 */
const picture = (one: Image): Image => ({
  ...one,
  url: apiUrl(one.url),
  renditions: one.renditions.map(rendition => ({...rendition, url: apiUrl(rendition.url)})),
})

const pictureOrNone = (one?: Image | null): Image | null => (one ? picture(one) : null)

/** A board's photograph and every portrait on it, resolved at the one seam they come through. */
const withPictures = (board: Board): Board => ({
  ...board,
  photo: pictureOrNone(board.photo),
  members: board.members.map(member => ({...member, portrait: pictureOrNone(member.portrait)})),
})

const withPortrait = (member: BoardMember): BoardMember =>
  ({...member, portrait: pictureOrNone(member.portrait)})

/**
 * The bytes somebody chose, put into storage for a save to name.
 *
 * Storing and applying are separate: the dialog that chose the picture is what puts it on the
 * board or the membership, so cancelling that dialog leaves both as they were. A refusal comes back
 * in the api's own words, because a picture the converter cannot read is the one thing whoever
 * chose it can act on.
 */
const storePicture = (kind: FileType): PictureStore => async (file: File) => {
  const stored = await refusable(uploadPublicImage({query: {type: kind}, body: {file}}), "That picture could not be stored.")
  return stored.ok ? {ok: true, saved: picture(stored.saved)} : stored
}

/** A board's group photograph, and one board member's portrait. Two kinds, so two stores. */
export const storeBoardPhoto: PictureStore = storePicture(FileType.BOARD_PHOTO)
export const storeMemberPortrait: PictureStore = storePicture(FileType.BOARD_PORTRAIT)

/**
 * A board member's name with its nickname back in the middle of it, the way the history was
 * written: `Roos "SkyeWolf" Kruk`. The two are recorded apart so anything can ask for either.
 */
export function memberTitle(member: {name?: string | null; nickname?: string | null}): string {
  const name = member.name ?? ""
  if (!member.nickname) return name
  const [first, ...rest] = name.split(" ")
  const quoted = `${first} "${member.nickname}"`
  return rest.length === 0 ? quoted : `${quoted} ${rest.join(" ")}`
}

/** Newest board first, which is the order the page reads them in. */
export async function loadBoards(): Promise<Board[]> {
  const res = await findAllBoards()
  return (res.data ?? [])
    .map(withPictures)
    .sort((left, right) => right.startDate.localeCompare(left.startDate))
}

/**
 * A board written down, or the api's own words for why it was not.
 *
 * A clashing number is the refusal this exists for: the api answers "Board 9 already exists",
 * and a dialog that could only report that something went wrong would leave whoever typed it
 * guessing at which field to change.
 */
export async function saveBoardOrReason(id: number | undefined, body: BoardRequest): Promise<Saved<Board> | Refused> {
  const saved = await refusable(
    id == null ? createBoard({body}) : updateBoard({path: {id}, body}),
    "That board could not be saved.",
  )
  return saved.ok ? {ok: true, saved: withPictures(saved.saved)} : saved
}

/** A board with members on it is refused, and the refusal says how many are in the way. */
export const dropBoard = (id: number): Promise<{ok: true} | Refused> =>
  accepted(deleteBoard({path: {id}}), "The board could not be removed.")

const portrayed = (saved: Saved<BoardMember> | Refused): Saved<BoardMember> | Refused =>
  saved.ok ? {ok: true, saved: withPortrait(saved.saved)} : saved

/**
 * A board membership written down, or the api's own words for why it was not.
 *
 * `displayName` is the name the membership stands under rather than the account's. Most of the
 * people who have held one never had an account here, so the name is the membership's own and an
 * account is something it may additionally have.
 */
export async function addMemberOrReason(
  boardId: number,
  body: AddBoardMemberRequest,
): Promise<Saved<BoardMember> | Refused> {
  return portrayed(await refusable(addMember({path: {boardId}, body}), "That member could not be added."))
}

export async function saveMemberOrReason(
  boardId: number,
  id: number,
  body: UpdateBoardMemberRequest,
): Promise<Saved<BoardMember> | Refused> {
  return portrayed(await refusable(updateMember({path: {boardId, id}, body}), "That member could not be saved."))
}

/** A null account detaches the membership, which keeps standing under its own name. */
export async function linkMemberAccountOrReason(
  boardId: number,
  id: number,
  userId: number | null,
): Promise<Saved<BoardMember> | Refused> {
  const what = userId == null ? "detached" : "linked to that account"
  return portrayed(await refusable(linkMember({path: {boardId, id}, body: {userId}}), `That member could not be ${what}.`))
}

/** A membership is somebody's place in the association's history, so a refusal is worth reporting. */
export const dropMemberOrReason = (boardId: number, id: number): Promise<{ok: true} | Refused> =>
  accepted(removeMember({path: {boardId, id}}), "That member could not be removed.")
