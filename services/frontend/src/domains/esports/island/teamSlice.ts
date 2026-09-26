import {sizeOf, srcsetOf} from "@/components/island/pictures"
import type {TeamRoster} from "../adapters/esports"
import type {RosterGroup} from "./TeamRoster.vue"

/** The roster as the pages have always read it: players, then substitutes, then coaches. */
const GROUPS = [
  {role: "PLAYER", one: "Player", many: "Players"},
  {role: "SUBSTITUTE", one: "Substitute", many: "Substitutes"},
  {role: "COACH", one: "Coach", many: "Coaches"},
] as const

/** A team's line-up as its open slice reveals it, the empty parts left out. */
export const rosterGroupsOf = (team: TeamRoster): RosterGroup[] => GROUPS
  .map(group => ({...group, members: team.members.filter(member => member.role === group.role)}))
  .filter(group => group.members.length > 0)

/**
 * A team as a slice: its own two pictures, the banner behind it and the icon beside its name. A
 * team nobody has given either to draws neither, and the slice reads on the band's accent.
 */
export const teamSliceOf = (team: TeamRoster) => ({
  id: team.id as number | string,
  title: team.name,
  meta: `${team.members.length} on the roster`,
  banner: team.banner?.url ?? "",
  srcset: srcsetOf(team.banner),
  ...sizeOf(team.banner),
  icon: team.icon?.url ?? null,
  iconSrcset: srcsetOf(team.icon),
})
