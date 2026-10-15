/** How the Discord pages word the server's channels: where each sits, who gets in and what it belongs to. */
import type {CataloguedChannel} from "./adapters/catalogue"

/** A role as the pages name it, with the cohort that fills it where the site keeps it. */
export type NamedRole = {id: string; name: string; follows: string | null}

export type ChannelGroup = {name: string; channels: CataloguedChannel[]}

const NO_CATEGORY = "No category"
const isArchive = (category: string) => /archive/i.test(category)

/** The server's channels by the category they are filed under, the uncategorised first and the archive last. */
export function channelGroups(channels: CataloguedChannel[]): ChannelGroup[] {
  const groups = new Map<string, CataloguedChannel[]>()
  for (const channel of channels.filter((one) => one.kind !== "CATEGORY")) {
    const name = channel.category ?? NO_CATEGORY
    groups.set(name, [...(groups.get(name) ?? []), channel])
  }
  const rank = (name: string) => (name === NO_CATEGORY ? 0 : isArchive(name) ? 2 : 1)
  return [...groups].map(([name, listed]) => ({name, channels: listed})).sort((a, b) => rank(a.name) - rank(b.name))
}

/** What a channel belongs to: its game, or the cohorts that fill the roles it opens to. */
export function belongsTo(channel: CataloguedChannel, roles: Map<string, NamedRole>): string {
  // A game can have both: a channel for playing it casually and one for its esports teams.
  if (channel.game) return `${channel.game.name}, ${channel.game.kind === "COMPETITION" ? "esports" : "casual"} channel`
  const follows = channel.roleIds.map((id) => roles.get(id)?.follows).filter((one): one is string => !!one)
  return follows.length > 0 ? follows.join(", ") : "Nothing"
}

/** Who gets into a channel: everyone, or only the roles a private channel opens to. */
export function accessOf(channel: CataloguedChannel, roles: Map<string, NamedRole>): string {
  if (!channel.private) return "Everyone"
  if (channel.roleIds.length === 0) return "Nobody"
  return `Only ${channel.roleIds.map((id) => `@${roles.get(id)?.name ?? id}`).join(", ")}`
}

/** What a role opens: its one channel by name, or how many categories and channels. */
export function opensOf(roleId: string, channels: CataloguedChannel[]): string {
  const opened = channels.filter((one) => one.roleIds.includes(roleId))
  const [only] = opened
  if (!only) return "Nothing"
  if (opened.length === 1) return only.kind === "CATEGORY" ? only.name : `#${only.name}`
  const categories = opened.filter((one) => one.kind === "CATEGORY").length
  const rest = opened.length - categories
  const counted = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`
  return [categories > 0 ? counted(categories, "category", "categories") : null, rest > 0 ? counted(rest, "channel", "channels") : null]
    .filter(Boolean)
    .join(", ")
}
