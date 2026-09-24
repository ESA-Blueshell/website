import {computed, ref, type ComputedRef, type Ref} from "vue"
import type {ArtCell} from "@/components/island/ArtCells.vue"
import type {DriftItem} from "@/components/island/DriftRow.vue"
import type {ReelItem} from "@/components/island/FlickReel.vue"
import {srcsetOf} from "@/components/island/pictures"
import {loadCasualGames, type CasualGame} from "./adapters/games"

/** The letters a game's plate carries where it has no banner: the first of its first two words. */
export function initialsOf(name: string): string {
  return name
    .replace(/[^\p{L}\p{N}\s]/gu, "")
    .split(/\s+/u)
    .filter(Boolean)
    .slice(0, 2)
    .map(word => word.charAt(0).toUpperCase())
    .join("")
}

/** A game nobody has drawn art for reads on the association's own blue. */
const UNDRAWN_ACCENT = "var(--color-brand)"

/** A game as the flick reel draws it. */
export function reelItemOf(game: CasualGame): ReelItem {
  return {
    id: game.code,
    title: game.name,
    href: `/casual/${game.slug}`,
    accent: game.accent || UNDRAWN_ACCENT,
    banner: game.banner?.url ?? null,
    srcset: srcsetOf(game.banner),
    icon: game.icon?.url ?? null,
    initials: initialsOf(game.name),
  }
}

/** An archived game as the games we used to play draw it. */
export function driftItemOf(game: CasualGame): DriftItem {
  const {id, title, href, accent, banner, srcset, initials} = reelItemOf(game)
  return {id, title, href, accent, banner, srcset, initials}
}

/** A game in Every game: archived ones tagged and toned down. */
export function cellOf(game: CasualGame): ArtCell {
  const {id, title, href, accent, banner, srcset, icon, initials} = reelItemOf(game)
  return {id, title, href, accent, banner, srcset, icon, initials, archived: game.archived}
}

const records = ref<CasualGame[]>([])
let asked: Promise<CasualGame[]> | null = null

/**
 * The games, read once and shared by every casual page and the home band.
 *
 * [refresh] asks again, for a page that has just changed a game.
 */
export function useCasualGames(): {
  games: Ref<CasualGame[]>
  live: ComputedRef<CasualGame[]>
  archived: ComputedRef<CasualGame[]>
  ready: Promise<CasualGame[]>
  refresh: () => Promise<CasualGame[]>
} {
  const refresh = async () => {
    records.value = await loadCasualGames()
    return records.value
  }
  asked ??= refresh()
  return {
    games: records,
    live: computed(() => records.value.filter(game => !game.archived)),
    archived: computed(() => records.value.filter(game => game.archived)),
    ready: asked,
    refresh,
  }
}

/** Lets the next caller read the games afresh. For tests, which each start from nothing. */
export function forgetCasualGames(): void {
  records.value = []
  asked = null
}
