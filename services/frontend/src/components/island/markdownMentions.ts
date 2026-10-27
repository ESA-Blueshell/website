import {type Completion, type CompletionContext, type CompletionResult, startCompletion} from "@codemirror/autocomplete"
import type {EditorView} from "@codemirror/view"
import {KINDS, literalAt} from "@/components/island/markdownLive"
import {type DiscordMentionChannelResponse, type DiscordRoleResponse, listServerChannels, listServerRoles, type MentionKind,
  mentionNamed, searchServerMembers} from "@/domains/discord"

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
// The longest nickname Discord allows.
const NAME_MAX = 32

/** A row of the `@` list: a member with their avatar, or a role with its colour. */
interface MentionOption extends Completion {
  avatar?: string
  colour?: number
}

const hex = (colour: number): string => `#${colour.toString(16).padStart(6, "0")}`

/*
 * A name runs on past a space, as `@The Old Man` does, for as long as a member or a role still
 * answers to it: a few words at most, the most a nickname holds, so a sentence after a stray `@`
 * asks nothing once nobody matches.
 */
const TYPED_NAME = /(?<=^|\s)@(?:[\w.-]+(?: [\w.-]*){0,5}|[\w.-]*)/

/**
 * `@` and a name: the members the server finds by it, then the roles whose name holds it. The
 * server matches usernames and nicknames the row does not show, so the editor's own filter is off
 * and each letter typed asks again.
 */
export const mentionCompletion = async (context: CompletionContext): Promise<CompletionResult | null> => {
  const started = context.matchBefore(TYPED_NAME)
  if (!started) return null
  const asked = started.text.slice(1).toLowerCase()
  if ((asked === "" && !context.explicit) || asked.length > NAME_MAX) return null

  const [people, ranks] = await Promise.all([
    asked.length >= SEARCHED_FROM ? searchServerMembers(asked) : Promise.resolve([]),
    serverRoles(),
  ])
  const options: MentionOption[] = [
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

/** A role's row hides the label CodeMirror draws, which cannot take a colour per role. */
export const mentionOptionClass = (completion: Completion): string => (completion.type === "role" ? "cm-option-role" : "")

/** Draws a member's avatar before their name, and a role's name in its colour in place of the label. */
export const mentionOption = {
  position: 20,
  render: (completion: Completion): Node | null => {
    const {type, avatar, colour} = completion as MentionOption
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
  if (before.trim() === "" && !/^[^\W_]/.test(asked)) return null

  const options = (await serverChannels())
    .filter(one => one.name.toLowerCase().includes(asked))
    .map(one => ({label: `#${one.name}`, apply: `<#${one.id}>`, type: "channel", ...(one.category ? {detail: one.category} : {})}))
  if (options.length === 0) return null
  return {from: started.from, options, validFor: /^#[\w-]*$/}
}

const MENTION_BEFORE = /<(@!?|@&|#)(\d{15,21})>$/

/**
 * Backspace just after a mention turns it back into what was typed, less its last letter, and
 * asks again, so a mention picked by mistake is mended by typing rather than deleted and redone.
 */
export const reopenMention = (view: EditorView): boolean => {
  const {state} = view
  const range = state.selection.main
  if (state.selection.ranges.length > 1 || !range.empty || literalAt(state, range.head, -1)) return false
  const line = state.doc.lineAt(range.head)
  const found = MENTION_BEFORE.exec(line.text.slice(0, range.head - line.from))
  const said = found && mentionNamed(KINDS[found[1] as string] as MentionKind, found[2] as string)
  if (!found || !said) return false
  const from = range.head - found[0].length
  const typed = said.slice(0, -1)
  view.dispatch({changes: {from, to: range.head, insert: typed}, selection: {anchor: from + typed.length}, userEvent: "delete.backward"})
  startCompletion(view)
  return true
}
