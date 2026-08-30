import {computed, ref, type ComputedRef, type Ref} from "vue"
import {loadSeasons, type Season} from "../adapters/esports"
import {newestSeason} from "./seasonAxis"

/**
 * The seasons the association has run, read once and shared.
 *
 * Both esports pages need this before they can ask anything else: the season a page shows is
 * the association's newest unless the url names another, and neither page can know which that
 * is from a game's own answer. Read once, because two pages and every reload asking the same
 * question of the api is the same answer four times over.
 *
 * The list is the one the strip is drawn from where the reader may edit, so a season written
 * down or taken away is written back here and both pages show it.
 */
const records = ref<Season[]>([])
let asked: Promise<Season[]> | null = null

export function useSeasons(): {
  seasons: Ref<Season[]>
  /** Resolves once the seasons have been read, so a page can wait before it asks anything. */
  ready: Promise<Season[]>
  /** The association's newest season, which is what a page shows when the url names none. */
  newest: ComputedRef<Season | null>
  refresh: () => Promise<Season[]>
} {
  const refresh = async () => {
    records.value = await loadSeasons()
    return records.value
  }

  asked ??= refresh()

  return {
    seasons: records,
    ready: asked,
    newest: computed(() => newestSeason(records.value)),
    refresh,
  }
}

/** Forgets what was read, so a test or a page that writes a season can ask again. */
export const forgetSeasons = () => {
  asked = null
  records.value = []
}
