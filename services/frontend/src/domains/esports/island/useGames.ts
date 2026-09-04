import {computed, ref, type ComputedRef, type Ref} from "vue"
import {loadGames, type GameCode, type Game} from "../adapters/esports"
import {sizeOf, srcsetOf} from "@/components/island/pictures"

/** A game as the island draws it: its name, its colour, and the art it carries. */
export interface GameIdentity {
  name: string
  /** The accent that carries this game across the island. */
  accent: string
  /** The game's own icon, where one has been chosen for it. */
  icon: string | null
  /** The widths that icon is stored at, ready for a `srcset`. */
  iconSrcset?: string
  /** The picture in the game's slice on the index, where one has been chosen for it. */
  banner: string | null
  /** The widths that picture is stored at, ready for a `srcset`. */
  srcset?: string
  /** Its own dimensions, so the browser reserves its space before the bytes arrive. */
  width?: number
  height?: number
}

/**
 * A game nobody has drawn art for reads on the association's own blue and shows no icon. Its
 * name still comes from its record; there is no game without one.
 */
const UNDRAWN = {accent: "var(--color-brand)", icon: null, banner: null}

const identify = (record: Game): GameIdentity => ({
  name: record.name,
  accent: record.accent || UNDRAWN.accent,
  icon: record.icon?.url ?? null,
  iconSrcset: srcsetOf(record.icon),
  banner: record.banner?.url ?? null,
  srcset: srcsetOf(record.banner),
  ...sizeOf(record.banner),
})

/**
 * The games the association knows, read once and shared.
 *
 * Which games exist, what each is called and the art each carries are one answer from the api,
 * so a page and the database cannot disagree. This is where the pages ask for it.
 */
const records = ref<Game[]>([])
let asked: Promise<Game[]> | null = null

export function useGames(): {
  games: Ref<Game[]>
  current: ComputedRef<Game[]>
  ready: Promise<Game[]>
  identityOf: (game: GameCode | string) => GameIdentity
  recordOf: (game: GameCode | string) => Game | null
  bySlug: (slug: string) => Game | null
  refresh: () => Promise<Game[]>
} {
  const refresh = async () => {
    records.value = await loadGames()
    return records.value
  }

  asked ??= refresh()

  const recordOf = (game: GameCode | string) => records.value.find(one => one.code === game) ?? null

  return {
    games: records,
    current: computed(() => records.value.filter(one => one.current)),
    ready: asked,
    identityOf: (game) => {
      const record = recordOf(game)
      // No record means the records have not answered yet, or the code names no game. Either
      // way there is no name to print: the raw code is not one, and showing it flashes.
      return record ? identify(record) : {name: "", ...UNDRAWN}
    },
    recordOf,
    bySlug: (slug) => records.value.find(one => one.slug === slug) ?? null,
    refresh,
  }
}

/** Forgets what was read, so a test or a page that writes a game can ask again. */
export const forgetGames = () => {
  asked = null
  records.value = []
}
