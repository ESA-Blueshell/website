import type {Completion, CompletionContext, CompletionResult} from "@codemirror/autocomplete"
import {type DiscordNameResponse, type DiscordRoleResponse, listServerChannels, listServerRoles, searchServerMembers}
  from "@/domains/discord"

/* A description holds a mention as Discord writes it: `<@id>`, `<@&id>` or `<#id>`. */

/* Read once an editor asks, and kept: roles and channels change rarely. */
let roles: Promise<DiscordRoleResponse[]> | undefined
let channels: Promise<DiscordNameResponse[]> | undefined
const serverRoles = () => (roles ??= listServerRoles().then(listed => listed ?? []))
const serverChannels = () => (channels ??= listServerChannels().then(listed => listed ?? []))

/** Forgets the roles and channels read, for a test that reads them again. */
export const forgetMentionLists = (): void => {
  roles = undefined
  channels = undefined
}

/* Discord searches members from two letters on. */
const SEARCHED_FROM = 2

/** `@` and a name: the members the server finds by it, then the roles whose name holds it. */
export const mentionCompletion = async (context: CompletionContext): Promise<CompletionResult | null> => {
  const started = context.matchBefore(/(?<=^|\s)@[\w.-]*/)
  if (!started) return null
  const asked = started.text.slice(1).toLowerCase()
  if (asked === "" && !context.explicit) return null

  const [people, ranks] = await Promise.all([
    asked.length >= SEARCHED_FROM ? searchServerMembers(asked) : Promise.resolve([]),
    serverRoles(),
  ])
  const options: Completion[] = [
    ...(people ?? []).map(one => ({label: `@${one.name}`, detail: one.username, apply: `<@${one.id}>`, type: "member"})),
    ...ranks.filter(one => one.name.toLowerCase().includes(asked))
      .map(one => ({label: `@${one.name}`, detail: "role", apply: `<@&${one.id}>`, type: "role"})),
  ]
  if (options.length === 0) return null
  return {
    from: started.from,
    options,
    // Typing on narrows what the search found; a search from fewer letters found no members yet.
    validFor: text => asked.length >= SEARCHED_FROM && text.toLowerCase().startsWith(`@${asked}`),
  }
}

/**
 * `#` and a name, in the middle of a line: the channels everybody can see. At the start of a
 * line `#` is a heading, so nothing is offered there.
 */
export const channelCompletion = async (context: CompletionContext): Promise<CompletionResult | null> => {
  const started = context.matchBefore(/#[\w-]*/)
  if (!started) return null
  const line = context.state.doc.lineAt(started.from)
  if (!/\S\s+$/.test(line.text.slice(0, started.from - line.from))) return null
  const asked = started.text.slice(1).toLowerCase()

  const options = (await serverChannels())
    .filter(one => one.name.toLowerCase().includes(asked))
    .map(one => ({label: `#${one.name}`, apply: `<#${one.id}>`, type: "channel"}))
  if (options.length === 0) return null
  return {from: started.from, options, validFor: /^#[\w-]*$/}
}
