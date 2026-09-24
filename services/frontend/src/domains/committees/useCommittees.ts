import {computed, ref, type ComputedRef, type Ref} from "vue"
import type {ArtCell} from "@/components/island/ArtCells.vue"
import type {DriftItem} from "@/components/island/DriftRow.vue"
import type {ReelItem} from "@/components/island/FlickReel.vue"
import {srcsetOf} from "@/components/island/pictures"
import {loadCommittees, type Committee} from "./adapters/committees"

/** A committee's plate letters where it has no banner: the first of its first two words. */
export function initialsOf(name: string): string {
  return name
    .replace(/[^\p{L}\p{N}\s]/gu, "")
    .split(/\s+/u)
    .filter(Boolean)
    .slice(0, 2)
    .map(word => word.charAt(0).toUpperCase())
    .join("")
}

/**
 * The first line of a committee's description with its markdown and emoji codes taken out, cut
 * at a word near [cap] characters.
 */
export function openingLineOf(description: string, cap = 140): string {
  const line = description
    .split(/\n/u)
    .map(one => one.replace(/:[a-z0-9_+-]+:/giu, "").replace(/[*_#>`[\]]/gu, "").replace(/\s+/gu, " ").trim())
    .find(one => one !== "") ?? ""
  if (line.length <= cap) return line
  return `${line.slice(0, line.lastIndexOf(" ", cap)).replace(/[,.;:]$/u, "")}...`
}

const ACCENT = "var(--color-brand)"

/** A committee as the flick reel draws it, with the games it organises events for as chips. */
export function reelItemOf(committee: Committee, gameNames: (codes: string[]) => string[] = () => []): ReelItem {
  return {
    id: committee.id,
    title: committee.name,
    href: `/committees/${committee.slug}`,
    accent: ACCENT,
    banner: committee.banner?.url ?? null,
    srcset: srcsetOf(committee.banner),
    initials: initialsOf(committee.name),
    railLabel: committee.name,
    chips: gameNames(committee.gameCodes),
  }
}

/** An archived committee as the committees we used to have draw it. */
export function driftItemOf(committee: Committee): DriftItem {
  const {id, title, href, accent, banner, srcset, initials} = reelItemOf(committee)
  return {id, title, href, accent, banner, srcset, initials, sub: openingLineOf(committee.description)}
}

/** A committee in Every committee: archived ones tagged and toned down. */
export function cellOf(committee: Committee, gameNames: (codes: string[]) => string[] = () => []): ArtCell {
  const {id, title, href, accent, banner, srcset, initials, chips} = reelItemOf(committee, gameNames)
  return {id, title, href, accent, banner, srcset, initials, chips, sub: openingLineOf(committee.description), archived: committee.archived}
}

const records = ref<Committee[]>([])
let asked: Promise<Committee[]> | null = null

/**
 * The committees, read once and shared by the committees pages and the game pages.
 *
 * Unlisted committees are left out of [listed] and everything built from it; [refresh] asks
 * again, for a page that has just changed one.
 */
export function useCommittees(): {
  committees: Ref<Committee[]>
  listed: ComputedRef<Committee[]>
  live: ComputedRef<Committee[]>
  archived: ComputedRef<Committee[]>
  ready: Promise<Committee[]>
  refresh: () => Promise<Committee[]>
} {
  const refresh = async () => {
    records.value = await loadCommittees()
    return records.value
  }
  asked ??= refresh()
  const listed = computed(() => records.value.filter(committee => committee.listed))
  return {
    committees: records,
    listed,
    live: computed(() => listed.value.filter(committee => !committee.archived)),
    archived: computed(() => listed.value.filter(committee => committee.archived)),
    ready: asked,
    refresh,
  }
}

/** Lets the next caller read the committees afresh. For tests, which each start from nothing. */
export function forgetCommittees(): void {
  records.value = []
  asked = null
}
