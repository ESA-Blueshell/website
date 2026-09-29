import {acceptCompletion, completionKeymap} from "@codemirror/autocomplete"
import {defaultKeymap, history, historyKeymap} from "@codemirror/commands"
import {deleteMarkupBackward, insertNewlineContinueMarkupCommand, markdown, markdownLanguage}
  from "@codemirror/lang-markdown"
import {HighlightStyle, syntaxHighlighting} from "@codemirror/language"
import {type EditorState, type Extension, Prec, Transaction, type TransactionSpec}
  from "@codemirror/state"
import {type EditorView, keymap} from "@codemirror/view"
import {tags} from "@lezer/highlight"
import {discordDialect, spoilerTag, subtextTag, underlineTag} from "@/components/island/markdownDialect"
import {emojiOnClose} from "@/components/island/markdownEmoji"
import {CODE_FONT, emojiLive, markdownLive, wrapWith} from "@/components/island/markdownLive"

/* What markdown looks like once it is being read rather than typed: as the Discord client draws
   it, since that is where a description is also read. */
const look = HighlightStyle.define([
  {tag: tags.heading1, fontSize: "1.5em", fontWeight: "700", lineHeight: "1.3"},
  {tag: tags.heading2, fontSize: "1.25em", fontWeight: "700", lineHeight: "1.3"},
  {tag: [tags.heading3, tags.heading4, tags.heading5, tags.heading6], fontWeight: "700"},
  {tag: tags.strong, fontWeight: "700"},
  {tag: tags.emphasis, fontStyle: "italic"},
  {tag: tags.strikethrough, textDecoration: "line-through", color: "var(--color-ash)"},
  {tag: tags.link, color: "var(--color-brand-lit)", textDecoration: "underline"},
  {tag: tags.url, color: "var(--color-ash)"},
  {tag: tags.monospace, fontFamily: CODE_FONT, fontSize: "0.85em"},
  {tag: tags.processingInstruction, color: "var(--color-ash)"},
  {tag: underlineTag, textDecoration: "underline", textUnderlineOffset: "3px"},
  // Readable while it is written: the dark block says it is a spoiler without hiding it.
  {tag: spoilerTag, backgroundColor: "color-mix(in oklab, var(--color-void) 75%, transparent)",
    borderRadius: "3px", padding: "0 0.15em"},
  {tag: subtextTag, fontSize: "0.8em", color: "var(--color-ash)"},
])

/* High, or `defaultKeymap`'s Mod-i selects the node around the cursor instead, and the next
   key typed replaces it. */
const marks = Prec.high(keymap.of([
  {key: "Mod-b", run: (at: EditorView) => wrapWith(at, "**")},
  {key: "Mod-i", run: (at: EditorView) => wrapWith(at, "*")},
]))

/* Enter on an empty item ends the list, as it does in Discord, rather than loosening it. */
const lists = keymap.of([
  {key: "Enter", run: insertNewlineContinueMarkupCommand({nonTightLists: false})},
  {key: "Backspace", run: deleteMarkupBackward},
])

/** The editor's language, keys and live preview, without anything that is the component's. */
export const markdownEditing: Extension[] = [
  history(),
  // Tab first, and only while the list is open, or it would stop leaving the editor.
  keymap.of([{key: "Tab", run: acceptCompletion}]),
  marks,
  lists,
  keymap.of([...completionKeymap, ...defaultKeymap, ...historyKeymap]),
  markdown({base: markdownLanguage, addKeymap: false, extensions: [discordDialect]}),
  syntaxHighlighting(look),
  markdownLive,
  emojiLive,
  emojiOnClose,
]

/**
 * A value the form sets, as a change to only what differs, so the cursor stays where the text
 * around it did not move. It is not the writer's to undo, and not held to the cap.
 */
export const replaceFromOutside = (state: EditorState, said: string): TransactionSpec => {
  const held = state.doc.toString()
  let start = 0
  while (start < held.length && start < said.length && held[start] === said[start]) start++
  let end = 0
  while (end < held.length - start && end < said.length - start
    && held[held.length - 1 - end] === said[said.length - 1 - end]) end++
  return {
    changes: {from: start, to: held.length - end, insert: said.slice(start, said.length - end)},
    annotations: Transaction.addToHistory.of(false),
    filter: false,
  }
}
