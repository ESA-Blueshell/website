import type {SliceItem} from "@/components/island/SliceBand.vue"
import type {LineupEntry} from "./useSeasonLineup"
import type {GameIdentity} from "./useGames"

/**
 * One game of a season's line-up as a slice: its name, colour and art from its record, how many
 * teams it fielded and the Discord channels its esports players meet in.
 *
 * A game entered with nobody in it says so, because only somebody who may edit is answered with
 * one: it is the board's list of what is left to do, and a visitor is not shown it at all.
 */
export function lineupSliceOf(entry: LineupEntry, identity: GameIdentity, href: string): SliceItem {
  const teams = entry.teams.length
  return {
    id: entry.game,
    href,
    title: identity.name,
    meta: [
      entry.public ? `${teams} team${teams === 1 ? "" : "s"} this season` : "no teams yet · not public",
      ...(identity.channels ?? []).map(name => `#${name}`),
    ].join(" · "),
    banner: identity.banner ?? "",
    srcset: identity.srcset,
    width: identity.width,
    height: identity.height,
    icon: identity.icon,
    iconSrcset: identity.iconSrcset,
    accent: identity.accent,
  }
}
