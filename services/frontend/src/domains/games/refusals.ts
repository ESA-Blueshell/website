// TWIN: `game/api/GameRefusal.kt` declares the codes and their facts, as `esports/domain/EsportsRefusal.kt`
// does for the ones esports still raises. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

interface RefusalBody extends RefusalCode {
  gameCode?: string
  gameName?: string
  given?: string
  address?: string
  teams?: number
  players?: number
}

export const plural = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`

const sentences: Record<string, (r: RefusalBody) => string> = {
  UnknownGameCode: r => `There is no game with the code '${r.gameCode}'.`,
  GameNameBlank: () => "A game needs a name.",
  GameNameUnusable: r => `'${r.given}' has no letters or digits to make a code from.`,
  GameAlreadyExists: r => `${r.gameName} is already a game.`,
  GameAddressBlank: () => "A game's page needs an address.",
  AddressReserved: r => `The address '${r.address}' is kept for the site's own pages.`,
  AddressTaken: r => `The address '${r.address}' is already used by ${r.gameName}.`,
  GameNotArchived: r => `${r.gameName} is still played. Archive it first, then it can be removed.`,
  GameHoldsHistory: r =>
    `${r.gameName ?? "That game"} holds ${plural(r.teams ?? 0, "team", "teams")} and `
    + `${plural(r.players ?? 0, "person", "people")} in competition, so it cannot be removed. `
    + "It stays archived, and everything it played stays readable.",
  GameArchived: r => `${r.gameName} is archived, so it cannot be newly picked.`,
  PictureNotStored: () => "That picture is not in storage.",
}

export const {sentenceFor, reasonFor} = refusalReader(sentences)
