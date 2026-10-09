import {defineComponent, h, type PropType, reactive, type VNode, type VNodeChild} from "vue"
import {DescriptionCellAlign, type DescriptionNode, DescriptionNodeKind, DescriptionTimeStyle} from "@/services/api"
import {timestampText, type TimeStyle} from "@/plugins/discordTime"
import {emojiSrc, serverEmojiSrc} from "@/plugins/emojiArt"
import {voiceRoomUrl} from "./adapters/widget"
import type {MentionIds} from "./adapters/mentions"
import type {MentionKind, MentionName} from "./mentions"

/*
 * A description drawn from the tree the api reads it into (architecture ADR-010): every node
 * kind is drawn here and nowhere else, and nothing in it is parsed again.
 */

export type NameOf = (kind: MentionKind, id: string) => MentionName | undefined

/** What a drawing reads besides the tree: the names of its mentions, and the pictures that would not load. */
interface Look {
  nameOf: NameOf
  broken: ReadonlySet<string>
  fail: (src: string) => void
}

const SIGNS: Record<MentionKind, string> = {user: "@", role: "@", channel: "#"}

interface Tables {
  letters: Record<DescriptionTimeStyle, TimeStyle>
  aligns: Record<DescriptionCellAlign, string>
  kinds: Partial<Record<DescriptionNodeKind, MentionKind>>
  wrappers: Partial<Record<DescriptionNodeKind, string>>
}

// Built when first read rather than at import, since a suite that mocks the client has no enums then.
let built: Tables | undefined
const tables = (): Tables => built ??= {
  letters: {
    [DescriptionTimeStyle.SHORT_TIME]: "t",
    [DescriptionTimeStyle.LONG_TIME]: "T",
    [DescriptionTimeStyle.SHORT_DATE]: "d",
    [DescriptionTimeStyle.LONG_DATE]: "D",
    [DescriptionTimeStyle.SHORT_DATE_TIME]: "f",
    [DescriptionTimeStyle.LONG_DATE_TIME]: "F",
    [DescriptionTimeStyle.RELATIVE]: "R",
  },
  aligns: {
    [DescriptionCellAlign.LEFT]: "left",
    [DescriptionCellAlign.CENTER]: "center",
    [DescriptionCellAlign.RIGHT]: "right",
  },
  kinds: {
    [DescriptionNodeKind.USER_MENTION]: "user",
    [DescriptionNodeKind.ROLE_MENTION]: "role",
    [DescriptionNodeKind.CHANNEL_MENTION]: "channel",
  },
  wrappers: {
    [DescriptionNodeKind.QUOTE]: "blockquote",
    [DescriptionNodeKind.ITEM]: "li",
    [DescriptionNodeKind.ROW]: "tr",
    [DescriptionNodeKind.STRONG]: "strong",
    [DescriptionNodeKind.EMPHASIS]: "em",
    [DescriptionNodeKind.UNDERLINE]: "u",
    [DescriptionNodeKind.STRIKE]: "del",
  },
}

/** The members, roles and channels a tree mentions, to be named in one read. */
export const mentionsOf = (nodes: DescriptionNode[]): MentionIds => {
  const ids = {users: new Set<string>(), roles: new Set<string>(), channels: new Set<string>()}
  const walk = (some: DescriptionNode[]) => {
    for (const node of some) {
      const kind = tables().kinds[node.kind]
      if (kind && node.id) {
        ids[`${kind}s`].add(node.id)
      }
      walk(node.children)
    }
  }
  walk(nodes)
  return {users: [...ids.users], roles: [...ids.roles], channels: [...ids.channels]}
}

const mention = (kind: MentionKind, id: string, look: Look): VNode => {
  const named = look.nameOf(kind, id)
  const pill = h("span", {
    "class": kind === "role" ? "mention mention--role" : "mention",
    [`data-${kind}`]: id,
    "style": named?.colour ? {"--mention": named.colour} : undefined,
  }, named?.said ?? `${SIGNS[kind]}…`)
  // The pill inside the link, so a card that unwraps its links keeps the pill to name.
  return kind === "channel" ? h("a", {href: voiceRoomUrl(id)}, [pill]) : pill
}

const table = (node: DescriptionNode, draw: (some: DescriptionNode[]) => VNodeChild[]): VNode => {
  const heads = node.children.filter(row => row.children.some(cell => cell.header))
  const rows = node.children.filter(row => !heads.includes(row))
  return h("table", [
    heads.length > 0 ? h("thead", draw(heads)) : null,
    rows.length > 0 ? h("tbody", draw(rows)) : null,
  ])
}

/** The nodes as elements; a tight list's items hold their lines without paragraphs around them. */
const drawNodes = (nodes: DescriptionNode[], look: Look, tight = false): VNodeChild[] =>
  nodes.map(node => drawNode(node, look, tight))

// A picture that would not load is the character it stands for, which the device draws as best it can.
const picture = (src: string, alt: string, look: Look): VNodeChild =>
  look.broken.has(src) ? alt : h("img", {class: "emoji", src, alt, draggable: "false", onError: () => look.fail(src)})

const drawNode = (node: DescriptionNode, look: Look, tight: boolean): VNodeChild => {
  const inner = (some = node.children) => drawNodes(some, look, node.kind === DescriptionNodeKind.LIST ? node.tight === true : false)
  const wrapper = tables().wrappers[node.kind]
  if (wrapper) return h(wrapper, node.kind === DescriptionNodeKind.ITEM ? drawNodes(node.children, look, tight) : inner())
  const mentioned = tables().kinds[node.kind]
  if (mentioned) return mention(mentioned, node.id ?? "", look)
  switch (node.kind) {
    case DescriptionNodeKind.PARAGRAPH:
      return tight ? inner() : h("p", inner())
    case DescriptionNodeKind.HEADING:
      return h(`h${Math.min(Math.max(node.level ?? 1, 1), 6)}`, inner())
    case DescriptionNodeKind.SUBTEXT:
      return h("p", {class: "subtext"}, inner())
    case DescriptionNodeKind.LIST:
      return node.ordered
        ? h("ol", node.number !== undefined && node.number !== null && node.number !== 1 ? {start: node.number} : {}, inner())
        : h("ul", inner())
    case DescriptionNodeKind.CODE_BLOCK:
      return h("pre", [h("code", node.language ? {class: `language-${node.language}`} : {}, (node.text ?? "").replace(/\n$/u, ""))])
    case DescriptionNodeKind.RULE:
      return h("hr")
    case DescriptionNodeKind.TABLE:
      return table(node, some => drawNodes(some, look))
    case DescriptionNodeKind.CELL:
      return h(node.header ? "th" : "td", node.align ? {align: tables().aligns[node.align]} : {}, inner())
    case DescriptionNodeKind.LINE_BREAK:
      return h("br")
    case DescriptionNodeKind.SPOILER:
      return h("span", {"class": "spoiler", "role": "button", "tabindex": "0", "aria-expanded": "false"}, inner())
    case DescriptionNodeKind.CODE:
      return h("code", node.text ?? "")
    case DescriptionNodeKind.LINK:
      return h("a", {href: node.href, title: node.title ?? undefined}, inner())
    case DescriptionNodeKind.IMAGE:
      return h("img", {src: node.href, alt: node.text ?? "", title: node.title ?? undefined})
    case DescriptionNodeKind.EMOJI:
      return picture(emojiSrc(node.text ?? ""), node.text ?? "", look)
    case DescriptionNodeKind.SERVER_EMOJI:
      return picture(serverEmojiSrc(node.id ?? "", node.animated === true), `:${node.name ?? ""}:`, look)
    case DescriptionNodeKind.TIMESTAMP: {
      const unix = node.unix ?? 0
      const style = tables().letters[node.style ?? DescriptionTimeStyle.SHORT_DATE_TIME]
      return h("time", {class: "timestamp", datetime: new Date(unix * 1000).toISOString()}, timestampText(unix, style))
    }
    default:
      return node.text ?? ""
  }
}

/** A description's tree, drawn, with its mentions named once [nameOf] knows them. */
export const DescriptionNodes = defineComponent({
  name: "DescriptionNodes",
  props: {
    nodes: {type: Array as PropType<DescriptionNode[]>, required: true},
    nameOf: {type: Function as PropType<NameOf>, required: true},
  },
  setup(props) {
    const broken = reactive(new Set<string>())
    return () => drawNodes(props.nodes, {nameOf: props.nameOf, broken, fail: src => broken.add(src)})
  },
})
