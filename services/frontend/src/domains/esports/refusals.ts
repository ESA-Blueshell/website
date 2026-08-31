/**
 * What a refused esports write says to the person who asked for it.
 *
 * The api used to write these sentences and the frontend printed them, which put pluralisation
 * and joining words in Kotlin — and left the removal paragraph implemented twice, once here for
 * the question asked before the act and once there for the refusal after it. The api answers a
 * code and the facts now, and this is the only place the wording lives.
 *
 * TWIN: `esports/domain/EsportsRefusal.kt` declares these codes and the facts each carries.
 * A code added there needs a sentence here, or the reader falls back to the api's own summary.
 * See ADR-026.
 */

/** How many of a thing, named singly or in the plural. */
export const countOf = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

/**
 * What a game holds, said once.
 *
 * Shared by the question the dialog asks before a removal — which reads the counts from
 * `GET /games/{game}/contents` — and the refusal the api answers after one. They said the same
 * thing in two places before, and drifted the moment either was edited.
 */
export const gameHoldsHistory = (gameName: string, teams: number, players: number) =>
  `${gameName} holds ${countOf(teams, "team", "teams")} and `
  + `${countOf(players, "person", "people")}, so it cannot be removed. `
  + "Everything it played stays readable, and it leaves the pages that show what the "
  + "association plays by not being entered in a season."

/** The facts a refusal carries, as the api's problem detail holds them. */
interface RefusalBody {
  code?: string
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

/**
 * The sentence for a refusal, or nothing where the api named a code this does not know.
 *
 * Nothing rather than a guess: the caller falls back to the api's own summary, which is a fixed
 * sentence per code and is still true, just less specific than one written here.
 */
export function sentenceFor(body: unknown): string | null {
  const refusal = body as RefusalBody | null | undefined
  const code = refusal?.code
  if (!code) return null
  return sentences[code]?.(refusal as RefusalBody) ?? null
}
