/**
 * Games domain adapter: every call the casual pages make to the api goes through here, and every
 * url a game's pictures carry is resolved against the api here and nowhere else.
 */
import {
  apiUrl,
  archiveGame,
  createCasualGame,
  findCasualGames,
  findGameHoldings,
  removeGame,
  updateCasualGame,
  uploadPublicImage,
} from "@/services/api"
import {FileType, type CasualGameResponse, type GameChannelResponse, type GameHoldingsResponse, type Image} from "@/services/api"
import type {Picture} from "@/components/island/pictures"
import type {Refused} from "@/types/api"
import {reasonFor} from "../refusals"

export type CasualGame = CasualGameResponse
export type GameHoldings = GameHoldingsResponse
export type GameChannel = GameChannelResponse

/** What the board writes about a game from the casual pages. */
export interface CasualGameDraft {
  name: string
  slug: string
  intro: string | null
  accent: string | null
  banner: string | null
  icon: string | null
  channels: GameChannel[]
  /** What the competition pages say; nothing lets them say [intro]. */
  competitionIntro: string | null
  esportsChannels: GameChannel[]
  /** Where it sits among the others; nothing puts a new game last and leaves a game where it is. */
  sortIndex?: number | null
}

export interface GameSaved {
  ok: true
  game: CasualGame
}

const image = (one?: Image | null): Image | null =>
  one ? {...one, url: apiUrl(one.url), renditions: one.renditions.map(copy => ({...copy, url: apiUrl(copy.url)}))} : null

const withArt = (game: CasualGame): CasualGame => ({...game, banner: image(game.banner), icon: image(game.icon)})

const body = (draft: CasualGameDraft) => ({
  name: draft.name,
  slug: draft.slug,
  intro: draft.intro ?? undefined,
  accent: draft.accent ?? undefined,
  banner: draft.banner ?? undefined,
  icon: draft.icon ?? undefined,
  channels: draft.channels,
  competitionIntro: draft.competitionIntro ?? undefined,
  esportsChannels: draft.esportsChannels,
  sortIndex: draft.sortIndex ?? undefined,
})

/** Every game, archived ones included, in the order they are shown; none where the api fails. */
export async function loadCasualGames(): Promise<CasualGame[]> {
  const res = await findCasualGames()
  return Array.isArray(res.data) ? res.data.map(withArt) : []
}

export async function addCasualGame(draft: CasualGameDraft): Promise<GameSaved | Refused> {
  const res = await createCasualGame({body: body(draft)})
  if (res.error || !res.data) return {ok: false, reason: reasonFor(res.error, "The game could not be added.")}
  return {ok: true, game: withArt(res.data)}
}

export async function saveCasualGame(code: string, draft: CasualGameDraft): Promise<GameSaved | Refused> {
  const res = await updateCasualGame({path: {game: code}, body: body(draft)})
  if (res.error || !res.data) return {ok: false, reason: reasonFor(res.error, "The game could not be saved.")}
  return {ok: true, game: withArt(res.data)}
}

export async function setGameArchived(code: string, archived: boolean): Promise<GameSaved | Refused> {
  const res = await archiveGame({path: {game: code}, body: {archived}})
  if (res.error || !res.data) {
    return {ok: false, reason: reasonFor(res.error, archived ? "The game could not be archived." : "The game could not be brought back.")}
  }
  return {ok: true, game: withArt(res.data)}
}

/** What removing a game would touch, or null where it could not be read. */
export async function loadGameHoldings(code: string): Promise<GameHoldings | null> {
  const res = await findGameHoldings({path: {game: code}})
  return res.data ?? null
}

export async function removeCasualGame(code: string): Promise<{ok: true} | Refused> {
  const res = await removeGame({path: {game: code}})
  if (res.error) return {ok: false, reason: reasonFor(res.error, "The game could not be removed.")}
  return {ok: true}
}

/** Stores a picture somebody chose for a game, as the kind of picture it is. */
async function storeGamePicture(file: File, kind: FileType): Promise<{ok: true; picture: Picture} | Refused> {
  const res = await uploadPublicImage({query: {type: kind}, body: {file}})
  if (res.error || !res.data) return {ok: false, reason: reasonFor(res.error, "That picture could not be stored.")}
  return {ok: true, picture: image(res.data) as Picture}
}

export const storeGameBanner = (file: File) => storeGamePicture(file, FileType.GAME_BANNER)
export const storeGameIcon = (file: File) => storeGamePicture(file, FileType.GAME_ICON)
