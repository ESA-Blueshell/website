import {$require} from "@/plugins/require.js"
import type {Game} from "../adapters/esports"

export interface GameIdentity {
  /** The accent that carries this game across the island. */
  accent: string
  /** The game's own mark, where the association has one bundled. */
  mark: string | null
  blurb: string
}

const IDENTITIES: Record<string, GameIdentity> = {
  LEAGUE_OF_LEGENDS: {
    accent: "var(--color-game-league)",
    mark: $require("@/assets/league.png"),
    blurb: "Five a side, on the rift",
  },
  CS2: {
    accent: "var(--color-game-cs2)",
    mark: $require("@/assets/cs2.png"),
    blurb: "Tactical, and unforgiving",
  },
  CSGO: {accent: "var(--color-game-cs2)", mark: $require("@/assets/cs2.png"), blurb: "Where it started"},
  VALORANT: {
    accent: "var(--color-game-valorant)",
    mark: $require("@/assets/valorant.png"),
    blurb: "Aim, plus everything else",
  },
  ROCKET_LEAGUE: {
    accent: "var(--color-game-rocket)",
    mark: $require("@/assets/rocketleague.png"),
    blurb: "Football, with rocket cars",
  },
  GEOGUESSR: {
    accent: "var(--color-game-geoguessr)",
    mark: $require("@/assets/geoguessrlogo.webp"),
    blurb: "Somewhere on a road, guessing",
  },
}

const FALLBACK: GameIdentity = {accent: "var(--color-brand)", mark: null, blurb: ""}

/** A game's colour and mark. Unknown games fall back to the association's own blue. */
export const identityOf = (game: Game | string): GameIdentity => IDENTITIES[game] ?? FALLBACK
