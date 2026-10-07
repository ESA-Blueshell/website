/*
 * The leaderboard side of the pinger domain. Like the paint adapter, this is a file the domain
 * lets a page in through (frontend ADR-001/002): a page reads the board, the live stream and the
 * member's own opt-in from here, never from the generated client.
 */
import {apiUrl, board, optIn, setOptIn, type HouseLineResponse, type LeaderboardResponse, type StandingResponse} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import {refusalReader, type Saved} from "@/utils/refusals"

export type Standing = StandingResponse
export type HouseLine = HouseLineResponse
export type Leaderboard = LeaderboardResponse

const {refusable} = refusalReader({})

/** An empty board, painted until the api answers and kept when the read is refused. */
export const EMPTY_LEADERBOARD: Leaderboard = {house: null, members: []}

/** The ranked members and the house line. The read is public, so this rarely falls back. */
export const loadLeaderboard = (): Promise<Leaderboard> => readOr(board(), EMPTY_LEADERBOARD)

/**
 * Opens the public stream and hands each snapshot to [onSnapshot]. Returns a close handle the
 * caller runs on unmount: an EventSource left open keeps reconnecting once the page is gone.
 */
export function openLeaderboardStream(onSnapshot: (snapshot: Leaderboard) => void): () => void {
  const source = new EventSource(apiUrl("/pinger/leaderboard/stream"))
  source.onmessage = (event: MessageEvent<string>) => {
    // A malformed frame is dropped, not thrown: the initial GET already painted a board to fall back on.
    try {
      onSnapshot(JSON.parse(event.data) as Leaderboard)
    } catch {
      // Nothing to paint from a frame that does not parse.
    }
  }
  return () => source.close()
}

/** Whether the signed-in member appears on the public board. Off until they choose otherwise. */
export async function loadOptIn(): Promise<boolean> {
  const answer = await readOr(optIn(), {optedIn: false})
  return answer.optedIn
}

/** Sets whether the signed-in member appears, answering with their saved choice or the refusal. */
export async function saveOptIn(optedIn: boolean): Promise<Saved<boolean> | Refused> {
  const result = await refusable(setOptIn({body: {optedIn}}), "Your choice could not be saved.")
  return result.ok ? {ok: true, saved: result.saved.optedIn} : result
}

/** The signed-in member's own row, matched by the username the api prints for an unlinked member. */
export const ownStanding = (snapshot: Leaderboard, username: string | null | undefined): Standing | null =>
  username ? snapshot.members.find(member => member.username === username) ?? null : null
