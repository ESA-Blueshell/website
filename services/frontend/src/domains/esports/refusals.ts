// TWIN: `esports/domain/EsportsRefusal.kt` declares the codes and their facts. See ADR-026.

import {countOf} from "./copy"
import {refusalReader, type RefusalCode} from "@/utils/refusals"

export const gameHoldsHistory = (gameName: string, teams: number, players: number) =>
  `${gameName} holds ${countOf(teams, "team", "teams")} and `
  + `${countOf(players, "person", "people")}, so it cannot be removed. `
  + "Everything it played stays readable, and it leaves the pages that show what the "
  + "association plays by not being entered in a season."

interface RefusalBody extends RefusalCode {
  gameCode?: string
  gameName?: string
  given?: string
  address?: string
  seasonName?: string
  teams?: number
  players?: number
}

const sentences: Record<string, (r: RefusalBody) => string> = {
  UnknownGameCode: r => `There is no game with the code '${r.gameCode}'.`,
  GameNameBlank: () => "A game needs a name.",
  GameNameUnusable: r => `'${r.given}' has no letters or digits to make a code from.`,
  GameAlreadyExists: r => `${r.gameName} is already a game.`,
  GameHoldsHistory: r => gameHoldsHistory(r.gameName ?? "That game", r.teams ?? 0, r.players ?? 0),
  GameFieldedInSeason: r =>
    `${r.gameName} still has ${countOf(r.teams ?? 0, "team", "teams")} in this season. `
    + "Drop them from the season first, and the game can be taken out of it.",
  GameAddressBlank: () => "A game's page needs an address.",
  AddressReserved: r => `The address '${r.address}' belongs to the esports index.`,
  AddressTaken: r => `The address '${r.address}' is already used by ${r.gameName}.`,
  SeasonDatesOverlap: r => `Those dates overlap ${r.seasonName}.`,
  SeasonEndsBeforeStart: () => "A season cannot end before it starts.",
  PictureNotStored: () => "That picture is not in storage.",
}

export const {sentenceFor, reasonFor} = refusalReader(sentences)
