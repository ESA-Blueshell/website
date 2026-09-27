import type {Completion, CompletionContext, CompletionResult} from "@codemirror/autocomplete"
import {EditorView} from "@codemirror/view"
import {emojiMatching, emojiNamed} from "@/components/island/discordEmoji"
import {literalAt} from "@/components/island/markdownLive"
import {pictureOf} from "@/plugins/emojiArt"

/* The document keeps the emoji itself, never its name: the name is only how it is typed. */

export const emojiCompletion = (context: CompletionContext): CompletionResult | null => {
  // A colon, then the letters of a name: `:spar`, and never the `: ` of a sentence.
  const started = context.matchBefore(/:[a-z0-9_+-]*/i)
  if (!started || (started.from === started.to && !context.explicit)) return null

  const asked = started.text.slice(1).toLowerCase()
  if (asked === "" && !context.explicit) return null

  const found = emojiMatching(asked)
  if (found.length === 0) return null

  return {
    from: started.from,
    options: found.map((one, rank) => ({
      label: `:${one.name}:`,
      apply: one.emoji,
      type: "emoji",
      // The editor scores its own matches, so the order the names were found in is kept.
      boost: 99 - rank,
    })),
    validFor: /^:[a-z0-9_+-]*$/i,
  }
}

/** Draws an entry's emoji the way the page will, before its name. */
export const emojiOption = {
  position: 20,
  render: (completion: Completion): Node => {
    const drawn = document.createElement("img")
    drawn.className = "cm-emoji"
    drawn.src = pictureOf(completion.apply as string)
    drawn.alt = ""
    return drawn
  },
}

/** Typing the colon that closes a known name turns the name into the emoji. */
export const emojiOnClose = EditorView.inputHandler.of((view, from, to, text) => {
  if (text !== ":" || from !== to || literalAt(view.state, from, -1)) return false
  const line = view.state.doc.lineAt(from)
  const typed = /:([a-z0-9_+-]+)$/i.exec(line.text.slice(0, from - line.from))
  if (!typed) return false
  const emoji = emojiNamed(typed[1] as string)
  if (!emoji) return false
  const start = from - typed[0].length
  view.dispatch({
    changes: {from: start, to, insert: emoji},
    selection: {anchor: start + emoji.length},
    userEvent: "input.type",
  })
  return true
})
