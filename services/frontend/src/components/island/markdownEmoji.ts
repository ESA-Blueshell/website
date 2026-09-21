import type {CompletionContext, CompletionResult} from "@codemirror/autocomplete"
import * as emoji from "node-emoji"

/* The document keeps the shortcode, which is what the renderer draws; the list shows the
   character it will become. */
const MOST = 12

export const emojiCompletion = (context: CompletionContext): CompletionResult | null => {
  // A colon, then the letters of a name: `:spar`, and never the `: ` of a sentence.
  const started = context.matchBefore(/:[a-z0-9_+-]*/i)
  if (!started || (started.from === started.to && !context.explicit)) return null

  const asked = started.text.slice(1)
  if (asked === "" && !context.explicit) return null

  // Shortest name first: the search answers in whatever order the dataset holds.
  const found = emoji.search(asked)
    .sort((a, b) => a.name.length - b.name.length || a.name.localeCompare(b.name))
    .slice(0, MOST)
  if (found.length === 0) return null

  return {
    from: started.from,
    options: found.map(one => ({
      label: `:${one.name}:`,
      detail: one.emoji,
      apply: `:${one.name}:`,
      type: "text",
      // The editor scores its own matches, so the shortest name is lifted rather than sorted.
      boost: Math.max(-99, 99 - one.name.length * 4),
    })),
    validFor: /^:[a-z0-9_+-]*$/i,
  }
}
