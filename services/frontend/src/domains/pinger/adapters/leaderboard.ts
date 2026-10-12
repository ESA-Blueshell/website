/*
 * The pinger domain's adapter. A page reads the final SNTPings board from here, never from the
 * generated client (frontend ADR-001/002).
 */
import {
  board,
  type FastestResponse,
  type HouseLineResponse,
  type LeaderboardResponse,
  type RecordResponse,
  type StandingResponse,
} from "@/services/api"
import {readOr} from "@/utils/answers"

export type Standing = StandingResponse
export type HouseLine = HouseLineResponse
export type FastestStanding = FastestResponse
export type CombinedRecord = RecordResponse
export type Leaderboard = LeaderboardResponse

/** An empty board, painted until the api answers and kept when the read is refused. */
export const EMPTY_LEADERBOARD: Leaderboard = {house: null, members: [], fastest: [], record: null}

/** The ranked members and the house line. The read is public, so this rarely falls back. */
export const loadLeaderboard = async (): Promise<Leaderboard> => readOr(board(), EMPTY_LEADERBOARD)

/** The signed-in member's own row, matched by the username the api prints for an unlinked member. */
export const ownStanding = (snapshot: Leaderboard, username: string | null | undefined): Standing | null =>
  username ? snapshot.members.find(member => member.username === username) ?? null : null
