/**
 * Games domain adapter: every call the casual pages make to the api goes through here, and every
 * url a game's pictures carry is resolved against the api here and nowhere else.
 */
import {apiUrl, findCasualGames} from "@/services/api"
import type {CasualGameResponse, Image} from "@/services/api"

export type CasualGame = CasualGameResponse

const image = (one?: Image | null): Image | null =>
  one ? {...one, url: apiUrl(one.url), renditions: one.renditions.map(copy => ({...copy, url: apiUrl(copy.url)}))} : null

const withArt = (game: CasualGame): CasualGame => ({...game, banner: image(game.banner), icon: image(game.icon)})

/** Every game, archived ones included, in the order they are shown; none where the api fails. */
export async function loadCasualGames(): Promise<CasualGame[]> {
  const res = await findCasualGames()
  return Array.isArray(res.data) ? res.data.map(withArt) : []
}
