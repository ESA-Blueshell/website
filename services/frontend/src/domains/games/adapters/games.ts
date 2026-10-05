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
import {FileType, type CasualGameRequest, type CasualGameResponse, type GameChannelResponse, type GameHoldingsResponse, type Image} from "@/services/api"
import type {Picture} from "@/components/island/pictures"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"
import {accepted, refusable} from "../refusals"

export type CasualGame = CasualGameResponse
export type GameHoldings = GameHoldingsResponse
export type GameChannel = GameChannelResponse

const image = (one?: Image | null): Image | null =>
  one ? {...one, url: apiUrl(one.url), renditions: one.renditions.map(copy => ({...copy, url: apiUrl(copy.url)}))} : null

const withArt = (game: CasualGame): CasualGame => ({...game, banner: image(game.banner), icon: image(game.icon)})

/** Every game, archived ones included, in the order they are shown; none where the api fails. */
export async function loadCasualGames(): Promise<CasualGame[]> {
  const res = await findCasualGames()
  return Array.isArray(res.data) ? res.data.map(withArt) : []
}

const withArtSaved = (saved: Saved<CasualGame> | Refused): Saved<CasualGame> | Refused =>
  saved.ok ? {ok: true, saved: withArt(saved.saved)} : saved

export const addCasualGame = async (body: CasualGameRequest): Promise<Saved<CasualGame> | Refused> =>
  withArtSaved(await refusable(createCasualGame({body}), "The game could not be added."))

export const saveCasualGame = async (code: string, body: CasualGameRequest): Promise<Saved<CasualGame> | Refused> =>
  withArtSaved(await refusable(updateCasualGame({path: {game: code}, body}), "The game could not be saved."))

/**
 * Adds one Discord channel to the game's own, and changes nothing else: the save says again
 * everything the record holds, since a field left empty in it is one taken away.
 */
export const addGameChannel = (game: CasualGame, channel: {id: string; guildId: string; name: string}): Promise<Saved<CasualGame> | Refused> =>
  saveCasualGame(game.code, {
    name: game.name,
    slug: game.slug,
    intro: game.intro?.trim() || null,
    accent: game.accent || null,
    banner: game.banner?.path ?? null,
    icon: game.icon?.path ?? null,
    channels: [...game.channels, channel],
    competitionIntro: game.competitionIntro?.trim() || null,
    esportsChannels: game.esportsChannels,
    sortIndex: game.sortIndex,
  })

export const setGameArchived = async (code: string, archived: boolean): Promise<Saved<CasualGame> | Refused> =>
  withArtSaved(await refusable(
    archiveGame({path: {game: code}, body: {archived}}),
    archived ? "The game could not be archived." : "The game could not be brought back.",
  ))

/** What removing a game would touch, or null where it could not be read. */
export async function loadGameHoldings(code: string): Promise<GameHoldings | null> {
  const res = await findGameHoldings({path: {game: code}})
  return res.data ?? null
}

export const removeCasualGame = (code: string): Promise<{ok: true} | Refused> =>
  accepted(removeGame({path: {game: code}}), "The game could not be removed.")

/** Stores a picture somebody chose for a game, as the kind of picture it is. */
async function storeGamePicture(file: File, kind: FileType): Promise<Saved<Picture> | Refused> {
  const stored = await refusable(uploadPublicImage({query: {type: kind}, body: {file}}), "That picture could not be stored.")
  return stored.ok ? {ok: true, saved: image(stored.saved) as Picture} : stored
}

export const storeGameBanner = (file: File) => storeGamePicture(file, FileType.GAME_BANNER)
export const storeGameIcon = (file: File) => storeGamePicture(file, FileType.GAME_ICON)
