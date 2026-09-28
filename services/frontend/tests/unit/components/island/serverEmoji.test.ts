import {afterEach, beforeAll, describe, expect, it, vi} from "vitest"
import {EditorSelection, EditorState} from "@codemirror/state"
import {CompletionContext} from "@codemirror/autocomplete"
import {EditorView} from "@codemirror/view"
import {emojiMatching, emojiNamed, loadDiscordEmoji, loadServerEmoji} from "@/components/island/discordEmoji"
import {markdownEditing} from "@/components/island/markdownEditing"
import {emojiCompletion, emojiOption} from "@/components/island/markdownEmoji"
import {listServerEmoji} from "@/domains/discord"

vi.mock("@/domains/discord", () => ({listServerEmoji: vi.fn()}))

const OWN = [
  {id: "657733730491826186", name: "POGGERS", animated: false},
  {id: "857733730491826187", name: "fire", animated: true},
]

beforeAll(async () => {
  vi.mocked(listServerEmoji).mockResolvedValue(OWN)
  await Promise.all([loadDiscordEmoji(), loadServerEmoji()])
})

const views: EditorView[] = []

afterEach(() => {
  for (const view of views.splice(0)) view.destroy()
})

const open = (doc: string, at: number) => {
  const view = new EditorView({
    parent: document.body,
    state: EditorState.create({doc, selection: EditorSelection.cursor(at), extensions: markdownEditing}),
  })
  views.push(view)
  view.contentDOM.focus()
  view.dispatch({selection: EditorSelection.cursor(at)})
  return view
}

describe("the server's own emoji", () => {
  it("are written as Discord writes them, whatever case the name is typed in", () => {
    expect(emojiNamed("poggers")).toBe("<:POGGERS:657733730491826186>")
    expect(emojiNamed("Fire")).toBe("<a:fire:857733730491826187>")
  })

  it("come before a standard emoji of the same name", () => {
    const found = emojiMatching("fire")

    expect(found[0]?.emoji).toBe("<a:fire:857733730491826187>")
    expect(found.map(one => one.emoji)).toContain("🔥")
  })

  it("are offered with their own picture", () => {
    const state = EditorState.create({doc: ":pogg"})
    const first = emojiCompletion(new CompletionContext(state, 5, false))?.options[0]

    expect(first?.apply).toBe("<:POGGERS:657733730491826186>")
    expect((emojiOption.render(first!) as HTMLImageElement).getAttribute("src"))
      .toBe("https://cdn.discordapp.com/emojis/657733730491826186.webp?size=48")
  })

  it("are written in when their name is closed with its colon", () => {
    const view = open(":POGGERS", 8)
    view.state.facet(EditorView.inputHandler)
      .some(handler => handler(view, 8, 8, ":", () => view.state.update({changes: {from: 8, insert: ":"}})))

    expect(view.state.doc.toString()).toBe("<:POGGERS:657733730491826186>")
  })

  it("are drawn as their picture, and shown as written while the cursor touches one", () => {
    const doc = "we <:POGGERS:657733730491826186> won"
    const drawn = open(doc, 0).contentDOM.querySelector<HTMLImageElement>("img.cm-emoji")

    expect(drawn?.getAttribute("src")).toBe("https://cdn.discordapp.com/emojis/657733730491826186.webp?size=48")
    expect(open(doc, 5).contentDOM.textContent).toBe(doc)
  })

  it("say their name where the picture will not load", () => {
    const view = open("we <a:party:123456789012345678> won", 0)
    const drawn = view.contentDOM.querySelector<HTMLImageElement>("img.cm-emoji")

    expect(drawn?.getAttribute("src")).toBe("https://cdn.discordapp.com/emojis/123456789012345678.gif?size=48")
    drawn?.dispatchEvent(new Event("error"))
    expect(view.contentDOM.textContent).toBe("we :party: won")
  })

  it("leave the editor with the standard emoji alone where the api cannot ask", async () => {
    vi.mocked(listServerEmoji).mockResolvedValue(null)
    await loadServerEmoji()

    expect(emojiNamed("poggers")).toBeUndefined()
    expect(emojiNamed("fire")).toBe("🔥")
  })
})
