import type {Completion, CompletionContext, CompletionResult} from "@codemirror/autocomplete"
import {type DiscordMentionChannelResponse, type DiscordRoleResponse, listServerChannels, listServerRoles, searchServerMembers}
  from "@/domains/discord"

/* A description holds a mention as Discord writes it: `<@id>`, `<@&id>` or `<#id>`. */

/* Read once an editor asks, and kept: roles and channels change rarely. */
let roles: Promise<DiscordRoleResponse[]> | undefined
let channels: Promise<DiscordMentionChannelResponse[]> | undefined
const serverRoles = () => (roles ??= listServerRoles().then(listed => listed ?? []))
const serverChannels = () => (channels ??= listServerChannels().then(listed => listed ?? []))

/** Forgets the roles and channels read, for a test that reads them again. */
export const forgetMentionLists = (): void => {
  roles = undefined
  channels = undefined
}

/* Discord searches members from two letters on. */
const SEARCHED_FROM = 2

/** A row of the `@` list: a member with their avatar, or a role with its colour. */
interface Mentioned extends Completion {
  avatar?: string
  colour?: number
}

const hex = (colour: number): string => `#${colour.toString(16).padStart(6, "0")}`

/**
 * `@` and a name: the members the server finds by it, then the roles whose name holds it. The
 * server matches usernames and nicknames the row does not show, so the editor's own filter is off
 * and each letter typed asks again.
 */
export const mentionCompletion = async (context: CompletionContext): Promise<CompletionResult | null> => {
  const started = context.matchBefore(/(?<=^|\s)@[\w.-]*/)
  if (!started) return null
  const asked = started.text.slice(1).toLowerCase()
  if (asked === "" && !context.explicit) return null

  const [people, ranks] = await Promise.all([
    asked.length >= SEARCHED_FROM ? searchServerMembers(asked) : Promise.resolve([]),
    serverRoles(),
  ])
  const options: Mentioned[] = [
    ...(people ?? []).map(one => ({label: one.name, detail: one.username, apply: `<@${one.id}>`, type: "member", avatar: one.avatar})),
    ...ranks.filter(one => one.name.toLowerCase().includes(asked)).map(one => ({
      label: `@${one.name}`,
      apply: `<@&${one.id}>`,
      type: "role",
      ...(one.colour ? {colour: one.colour} : {}),
    })),
  ]
  if (options.length === 0) return null
  return {from: started.from, options, filter: false}
}

/**
 * Draws a member's avatar before their name, and a role's name in its colour. The row's own label
 * cannot take a colour per role, so a role's row hides it (`cm-option-role`) for this one.
 */
export const mentionRow = (completion: Completion): string => (completion.type === "role" ? "cm-option-role" : "")

export const mentionOption = {
  position: 20,
  render: (completion: Completion): Node | null => {
    const {type, avatar, colour} = completion as Mentioned
    if (type === "member" && avatar) {
      const drawn = document.createElement("img")
      drawn.className = "cm-avatar"
      drawn.src = avatar
      drawn.alt = ""
      return drawn
    }
    if (type === "role") {
      const named = document.createElement("span")
      named.className = "cm-role"
      named.textContent = completion.label
      if (colour) named.style.setProperty("--mention", hex(colour))
      return named
    }
    return null
  },
}

/**
 * `#` and a name: the channels everybody can see, with the category each is filed under. At the
 * start of a line only `#` right before a letter asks, since `# ` there begins a heading.
 */
export const channelCompletion = async (context: CompletionContext): Promise<CompletionResult | null> => {
  const started = context.matchBefore(/#[\w-]*/)
  if (!started) return null
  const line = context.state.doc.lineAt(started.from)
  const before = line.text.slice(0, started.from - line.from)
  if (!/(^|\s)$/.test(before)) return null
  const asked = started.text.slice(1).toLowerCase()
  if (asked === "" && before.trim() === "" && !context.explicit) return null

  const options = (await serverChannels())
    .filter(one => one.name.toLowerCase().includes(asked))
    .map(one => ({label: `#${one.name}`, apply: `<#${one.id}>`, type: "channel", ...(one.category ? {detail: one.category} : {})}))
  if (options.length === 0) return null
  return {from: started.from, options, validFor: /^#[\w-]*$/}
}
