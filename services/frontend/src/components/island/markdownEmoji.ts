import type {CompletionContext, CompletionResult} from "@codemirror/autocomplete"
import * as emoji from "node-emoji"

/**
 * What is kept is the shortcode, since that is what the renderer turns into a character; the
 * list shows the character so the choice is still obvious.
 */
const MOST = 12

export const emojiCompletion = (context: CompletionContext): CompletionResult | null => {
  // A colon, then the letters of a name: `:spar`, and never the `: ` of a sentence.
  const started = context.matchBefore(/:[a-z0-9_+-]*/i)
  if (!started || (started.from === started.to && !context.explicit)) return null

  const asked = started.text.slice(1)
  if (asked === "" && !context.explicit) return null

  // Shortest name first: somebody who typed `fire` wants 🔥 before a fire engine, and the
  // search answers in the order the dataset happens to hold.
  const found = emoji.search(asked)
    .sort((a, b) => a.name.length - b.name.length || a.name.localeCompare(b.name))
    .slice(0, MOST)
  if (found.length === 0) return null

  return {
    from: started.from,
    options: found.map(one => ({
      label: `:${one.name}:`,
      detail: one.emoji,
      // The shortcode is what lands in the document; the character is what the row shows.
      apply: `:${one.name}:`,
      type: "text",
      // The editor scores its own matches, so the shortest name is lifted rather than sorted:
      // somebody who typed `fire` wants the fire before the fire engine.
      boost: Math.max(-99, 99 - one.name.length * 4),
    })),
    // The list narrows as more is typed rather than being asked for again.
    validFor: /^:[a-z0-9_+-]*$/i,
  }
}
