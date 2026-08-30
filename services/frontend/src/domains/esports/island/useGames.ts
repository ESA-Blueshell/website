import {computed, ref, type ComputedRef, type Ref} from "vue"
import {$require} from "@/plugins/require.js"
import {loadGames, type Game, type GameRecord} from "../adapters/esports"
import {sizeOf, srcsetOf} from "../pictures"

/** A game as the island draws it: its name, its colour, and the art it carries. */
export interface GameIdentity {
  name: string
  /** The accent that carries this game across the island. */
  accent: string
  /** The game's own mark, where one has been chosen for it. */
  mark: string | null
  /** The picture in the game's slice on the index, where one has been chosen for it. */
  banner: string | null
  /** The widths that picture is stored at, ready for a `srcset`. */
  srcset?: string
  /** Its own dimensions, so the browser reserves its space before the bytes arrive. */
  width?: number
  height?: number
}

/**
 * A game nobody has drawn art for reads on the association's own blue and shows no mark. Its
 * name still comes from its record; there is no game without one.
 */
const UNDRAWN = {accent: "var(--color-brand)", mark: null, banner: null}

// $require answers with an empty string for an asset that is not bundled, which reads the same
// as a game that never named one: no mark rather than a broken image.
const asset = (file: string | null | undefined): string | null =>
  (file ? $require(`@/assets/${file}`) || null : null)

const identify = (record: GameRecord): GameIdentity => ({
  name: record.name,
  accent: record.accent || UNDRAWN.accent,
  mark: asset(record.mark),
  banner: record.banner?.url ?? null,
  srcset: srcsetOf(record.banner),
  ...sizeOf(record.banner),
})

/**
 * The games the association knows, read once and shared.
 *
 * Which games exist, what each is called and the art each carries used to be written into the
 * frontend, so the pages and the database could disagree about both. They are one answer now,
 * and this is where the pages ask for it.
 */
const records = ref<GameRecord[]>([])
let asked: Promise<GameRecord[]> | null = null

export function useGames(): {
  games: Ref<GameRecord[]>
  fielded: ComputedRef<GameRecord[]>
  ready: Promise<GameRecord[]>
  identityOf: (game: Game | string) => GameIdentity
  recordOf: (game: Game | string) => GameRecord | null
  bySlug: (slug: string) => GameRecord | null
  refresh: () => Promise<GameRecord[]>
} {
  const refresh = async () => {
    records.value = await loadGames()
    return records.value
  }

  asked ??= refresh()

  const recordOf = (game: Game | string) => records.value.find(one => one.game === game) ?? null

  return {
    games: records,
    fielded: computed(() => records.value.filter(one => one.fielded)),
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
