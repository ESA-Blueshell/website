/**
 * Board domain adapter — the only file in this domain that imports from @/services/api
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
import type {BoardMemberResponse, BoardResponse, Image} from "@/services/api"
import type {PictureStore} from "@/components/island/pictures"
import {reasonFor} from "../refusals"

export type Board = BoardResponse
export type BoardSeat = BoardMemberResponse

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
  members: board.members.map(seat => ({...seat, portrait: pictureOrNone(seat.portrait)})),
})

const withPortrait = (seat: BoardSeat): BoardSeat => ({...seat, portrait: pictureOrNone(seat.portrait)})

/**
 * The bytes somebody chose, put into storage for a save to name.
 *
 * Storing and applying are separate: the dialog that chose the picture is what puts it on the
 * board or the seat, so cancelling that dialog leaves both as they were. A refusal comes back
 * in the api's own words, because a picture the converter cannot read is the one thing whoever
 * chose it can act on.
 */
const storePicture = (kind: FileType): PictureStore => async (file: File) => {
  const res = await uploadPublicImage({query: {type: kind}, body: {file}})
  if (res.error || !res.data) {
    const body = res.error as {detail?: string; title?: string} | null
    return {ok: false, reason: body?.detail || body?.title || "That picture could not be stored."}
  }
  return {ok: true, picture: picture(res.data)}
}

/** A board's group photograph, and one seat's portrait. Two kinds, so two stores. */
export const storeBoardPhoto: PictureStore = storePicture(FileType.BOARD_PHOTO)
export const storeSeatPortrait: PictureStore = storePicture(FileType.BOARD_PORTRAIT)

/**
 * A seat's name with its nickname back in the middle of it, the way the history was written:
 * `Roos "SkyeWolf" Kruk`. The two are recorded apart so anything can ask for either.
 */
export function seatTitle(seat: BoardSeat): string {
  const name = seat.name ?? ""
  if (!seat.nickname) return name
  const [first, ...rest] = name.split(" ")
  const quoted = `${first} "${seat.nickname}"`
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
 * A board as it is written down: everything the api records, and the key where one exists.
 *
 * `candidate` is passed through rather than composed here. The column is `NOT NULL`, nothing
 * reads it, and the api fills it with the board's name — or with its number where there is no
 * name — for a write that carries none. A second copy of that rule on this side would be a
 * second thing to keep in step.
 */
export interface BoardWrite {
  id?: number
  number: number
  name?: string | null
  candidate?: string | null
  cheer?: string | null
  accent?: string | null
  description?: string | null
  startDate: string
  endDate?: string | null
  image?: string | null
  photo?: string | null
  version?: number
}

/**
 * A board written down, or the api's own words for why it was not.
 *
 * A clashing number is the refusal this exists for: the api answers "Board 9 already exists",
 * and a dialog that could only report that something went wrong would leave whoever typed it
 * guessing at which field to change.
 */
export async function saveBoardOrReason(
  board: BoardWrite,
): Promise<{ok: true; board: Board} | Refused> {
  const body = {
    number: board.number,
    name: board.name ?? undefined,
    candidate: board.candidate ?? undefined,
    cheer: board.cheer ?? undefined,
    accent: board.accent ?? undefined,
    description: board.description ?? undefined,
    startDate: board.startDate,
    endDate: board.endDate ?? undefined,
    image: board.image ?? undefined,
    photo: board.photo ?? undefined,
  }
  const res = board.id == null
    ? await createBoard({body})
    : await updateBoard({path: {id: board.id}, body: {...body, version: board.version ?? 0}})
  if (res.error || !res.data) {
    return {ok: false, reason: reasonFor(res.error, "That board could not be saved.")}
  }
  return {ok: true, board: withPictures(res.data)}
}

export async function saveBoard(board: BoardWrite): Promise<Board | null> {
  const saved = await saveBoardOrReason(board)
  return saved.ok ? saved.board : null
}

/**
 * A write the api refused, in its own words.
 *
 * The sdk hands a refusal back as a body rather than throwing, so a caller that only reads
 * `data` cannot tell a rejection from a success and a `try/catch` catches nothing.
 */
export interface Refused {
  ok: false
  reason: string
}

/** A board with seats on it is refused, and the refusal says how many are in the way. */
export async function dropBoard(id: number): Promise<{ok: true} | Refused> {
  const res = await deleteBoard({path: {id}})
  if (res.error) return {ok: false, reason: reasonFor(res.error, "The board could not be removed.")}
  return {ok: true}
}

export async function addSeat(
  boardId: number,
  seat: {
    role: string
    startDate: string
    endDate?: string | null
    userId?: number | null
    displayName?: string | null
    nickname?: string | null
    description?: string | null
    image?: string | null
    portrait?: string | null
  },
): Promise<BoardSeat | null> {
  const res = await addMember({
    path: {boardId},
    body: {
      role: seat.role,
      startDate: seat.startDate,
      endDate: seat.endDate ?? undefined,
      userId: seat.userId ?? undefined,
      displayName: seat.displayName ?? undefined,
      nickname: seat.nickname ?? undefined,
      description: seat.description ?? undefined,
      image: seat.image ?? undefined,
      portrait: seat.portrait ?? undefined,
    },
  })
  return res.data ? withPortrait(res.data) : null
}

export async function saveSeat(
  boardId: number,
  id: number,
  seat: {
    role: string
    startDate: string
    endDate?: string | null
    displayName?: string | null
    nickname?: string | null
    description?: string | null
    image?: string | null
    portrait?: string | null
  },
): Promise<BoardSeat | null> {
  const res = await updateMember({
    path: {boardId, id},
    body: {
      role: seat.role,
      startDate: seat.startDate,
      endDate: seat.endDate ?? undefined,
      displayName: seat.displayName ?? undefined,
      nickname: seat.nickname ?? undefined,
      description: seat.description ?? undefined,
      image: seat.image ?? undefined,
      portrait: seat.portrait ?? undefined,
    },
  })
  return res.data ? withPortrait(res.data) : null
}

/** A null member detaches the seat, which keeps standing under its own name. */
export async function linkSeatMember(
  boardId: number,
  id: number,
  userId: number | null,
): Promise<BoardSeat | null> {
  const res = await linkMember({path: {boardId, id}, body: {userId: userId ?? undefined}})
  return res.data ? withPortrait(res.data) : null
}

export async function dropSeat(boardId: number, id: number): Promise<void> {
  await removeMember({path: {boardId, id}})
}
