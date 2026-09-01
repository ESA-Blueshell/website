/**
 * Board domain adapter — the only file in this domain that imports from @/services/api
 * (per frontend ADR-002). Everything else imports from here.
 */
import {
  addMember,
  createBoard,
  deleteBoard,
  findAllBoards,
  linkMember,
  removeMember,
  updateBoard,
  updateMember,
} from "@/services/api"
import type {BoardMemberResponse, BoardResponse} from "@/services/api"

export type Board = BoardResponse
export type BoardSeat = BoardMemberResponse

/** What a board is called: its own name, or its number where it has none recorded. */
export function boardTitle(board: Board): string {
  return board.name ?? `Board ${board.number}`
}

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
  return (res.data ?? []).slice().sort((left, right) => right.startDate.localeCompare(left.startDate))
}

export async function saveBoard(
  board: {
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
    version?: number
  },
): Promise<Board | null> {
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
  }
  const res = board.id == null
    ? await createBoard({body})
    : await updateBoard({path: {id: board.id}, body: {...body, version: board.version ?? 0}})
  return res.data ?? null
}

export async function dropBoard(id: number): Promise<void> {
  await deleteBoard({path: {id}})
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
    },
  })
  return res.data ?? null
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
    },
  })
  return res.data ?? null
}

/** A null member detaches the seat, which keeps standing under its own name. */
export async function linkSeatMember(
  boardId: number,
  id: number,
  userId: number | null,
): Promise<BoardSeat | null> {
  const res = await linkMember({path: {boardId, id}, body: {userId: userId ?? undefined}})
  return res.data ?? null
}

export async function dropSeat(boardId: number, id: number): Promise<void> {
  await removeMember({path: {boardId, id}})
}
