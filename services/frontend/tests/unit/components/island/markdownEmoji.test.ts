import {beforeAll, describe, expect, it} from "vitest"
import {EditorSelection, EditorState} from "@codemirror/state"
import {CompletionContext} from "@codemirror/autocomplete"
import {EditorView} from "@codemirror/view"
import {emojiMatching, emojiNamed, loadDiscordEmoji} from "@/components/island/discordEmoji"
import {markdownEditing} from "@/components/island/markdownEditing"
import {emojiCompletion, emojiOption} from "@/components/island/markdownEmoji"

beforeAll(() => loadDiscordEmoji())

const asking = (doc: string) => {
  const state = EditorState.create({doc})
  return emojiCompletion(new CompletionContext(state, doc.length, false))
}

/* Typed as a browser types it: the colon reaches the editor's input handlers first. */
const typeColon = (doc: string) => {
  const view = new EditorView({
    parent: document.body,
    state: EditorState.create({doc, selection: EditorSelection.cursor(doc.length), extensions: markdownEditing}),
  })
  const at = doc.length
  const handled = view.state.facet(EditorView.inputHandler)
    .some(handler => handler(view, at, at, ":", () => view.state.update({changes: {from: at, insert: ":"}})))
  if (!handled) view.dispatch({changes: {from: at, insert: ":"}})
  const said = view.state.doc.toString()
  view.destroy()
  return said
}

describe("emoji by the names Discord types them with", () => {
  it("knows Discord's names, which are not the ones every list uses", () => {
    expect(emojiNamed("thumbsup")).toBe("👍️")
    expect(emojiNamed("slight_smile")).toBe("\u{1F642}")
    expect(emojiNamed("flag_nl")).toBe("🇳🇱")
    expect(emojiNamed("spaghetti")).toBe("\u{1F35D}")
  })

  it("has every JoyPixels name in the list it reads emoji from", async () => {
    const {default: shortcodes} = await import("emojibase-data/en/shortcodes/joypixels.json")
    for (const named of Object.values(shortcodes as Record<string, string | string[]>)) {
      expect(emojiNamed([named].flat()[0] as string)).toBeTruthy()
    }
  })

  it("keeps the variation selector an emoji is written with", () => {
    expect(emojiNamed("heart")).toBe("❤️")
  })

  it("puts names that start with what was typed before names that only contain it", () => {
    const found = emojiMatching("fire").map(one => one.name)

    expect(found[0]).toBe("fire")
    expect(found.indexOf("fire_engine")).toBeLessThan(found.indexOf("campfire") === -1 ? Infinity : found.indexOf("campfire"))
  })
})

describe("the emoji completion", () => {
  it("offers what a half-typed name could be", () => {
    expect(asking("we are :spark")?.options.map(one => one.label)).toContain(":sparkles:")
  })

  it("writes the emoji itself, never its name", () => {
    const first = asking("we are :fire")?.options[0]

    expect(first?.label).toBe(":fire:")
    expect(first?.apply).toBe("🔥")
  })

  it("draws each entry's emoji as the page will, and nothing beside a row that is not an emoji", () => {
    const drawn = emojiOption.render({label: ":fire:", apply: "🔥", type: "emoji"}) as HTMLImageElement

    expect(drawn.getAttribute("src")).toBe("/emoji/1f525.svg")
    expect(emojiOption.render({label: "@Board", apply: "<@&901>", type: "role"})).toBeNull()
  })

  it("says nothing about a colon that starts no name", () => {
    expect(asking("the time is 10:")).toBeNull()
    expect(asking("nothing here")).toBeNull()
  })

  it("says nothing where no emoji answers to it", () => {
    expect(asking(":zzzzqqq")).toBeNull()
  })
})

describe("a name closed with its colon", () => {
  it("becomes the emoji", () => {
    expect(typeColon("lunch is :spaghetti")).toBe("lunch is \u{1F35D}")
    expect(typeColon(":Thumbsup")).toBe("👍️")
  })

  it("stays a name where no emoji answers to it", () => {
    expect(typeColon("at :notanemoji")).toBe("at :notanemoji:")
  })

  it("stays a name inside code", () => {
    expect(typeColon("```\n:fire")).toBe("```\n:fire:")
  })

  it("leaves a colon after a word alone", () => {
    expect(typeColon("Note")).toBe("Note:")
  })

  it("leaves a time alone", () => {
    expect(typeColon("at 20:00")).toBe("at 20:00:")
  })
})
