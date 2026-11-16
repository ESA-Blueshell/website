import type {VoteKind} from "./adapters/vote"

/** How a page presents one kind of annual vote. */
export interface VoteKindPage {
  kind: VoteKind
  /** The tab's address, shared by the nav entry and the route so the two cannot drift. */
  path: string
  routeName: string
  /** Whether the tab is shown at all: one flag takes the nav entry and the route away together. */
  enabled: boolean
  /** The name in the nav and the tab. */
  title: string
  /** The line under the heading, said to anybody who opens the page. */
  intro: string
}

export const OST_TOP_50: VoteKindPage = {
  kind: "OST_TOP_50",
  path: "/events/ost-top-50",
  routeName: "ostTop50",
  enabled: true,
  title: "OST Top 50",
  intro: "Pick the five game soundtracks you love most, each with the one track that sums it up. "
    + "Every member's ballot counts toward the association's OST Top 50.",
}

// Off until the board settles how many picks it takes and how long its top is.
export const GAME_OF_THE_YEAR: VoteKindPage = {
  kind: "GAME_OF_THE_YEAR",
  path: "/events/game-of-the-year",
  routeName: "gameOfTheYear",
  enabled: false,
  title: "Game of the Year",
  intro: "Rank the five games released this year that you loved most. "
    + "Every member's ballot counts toward Blueshell's Game of the Year.",
}

export const VOTE_KIND_PAGES: VoteKindPage[] = [OST_TOP_50, GAME_OF_THE_YEAR]
