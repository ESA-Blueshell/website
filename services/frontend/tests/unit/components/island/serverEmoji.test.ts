import {afterEach, beforeAll, describe, expect, it, vi} from "vitest"
import {EditorSelection, EditorState} from "@codemirror/state"
import {autocompletion, CompletionContext, completionStatus, currentCompletions, startCompletion} from "@codemirror/autocomplete"
import {EditorView} from "@codemirror/view"
import {emojiMatching, emojiNamed, loadDiscordEmoji, loadServerEmoji} from "@/components/island/discordEmoji"
import {markdownEditing} from "@/components/island/markdownEditing"
import {emojiCompletion, emojiOnArrival, emojiOption, shortcodesWritten} from "@/components/island/markdownEmoji"
import {listServerEmoji} from "@/domains/discord"
import {DiscordEmojiKind, type DiscordEmojiResponse} from "@/services/api"

vi.mock("@/domains/discord", async () => ({
  DiscordEmojiKind: (await vi.importActual<typeof import("@/services/api")>("@/services/api")).DiscordEmojiKind,
  listServerEmoji: vi.fn(),
}))

const OWN: DiscordEmojiResponse[] = [
  {id: "657733730491826186", name: "POGGERS", animated: false, kind: DiscordEmojiKind.SERVER},
  {id: "857733730491826187", name: "fire", animated: true, kind: DiscordEmojiKind.SERVER},
  {id: "857733730491826190", name: "bs_campfire", animated: false, kind: DiscordEmojiKind.SERVER},
  {id: "957733730491826188", name: "firework_shelly", animated: false, kind: DiscordEmojiKind.APPLICATION},
  {id: "957733730491826189", name: "shellyfire", animated: false, kind: DiscordEmojiKind.APPLICATION},
  {id: "957733730491826190", name: "firefly_bs", animated: false, kind: DiscordEmojiKind.APPLICATION},
  {id: "757733730491826191", name: "bigfire", animated: false, kind: DiscordEmojiKind.SEEN, uses: 50},
  {id: "757733730491826192", name: "firecat", animated: false, kind: DiscordEmojiKind.SEEN, uses: 1},
  {id: "757733730491826193", name: "firedog", animated: false, kind: DiscordEmojiKind.SEEN, uses: 9},
  {id: "757733730491826194", name: "kekw", animated: false, kind: DiscordEmojiKind.SEEN, uses: 2},
  {id: "757733730491826195", name: "KEKW", animated: false, kind: DiscordEmojiKind.SEEN, uses: 40},
  {id: "757733730491826196", name: "kekw", animated: false, kind: DiscordEmojiKind.SEEN, uses: 5},
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

  it("rank the server's own first, then the application's, then the seen ones by use, then the standard ones", () => {
    const found = emojiMatching("fire").map(one => one.emoji)

    expect(found.slice(0, 8)).toEqual([
      "<a:fire:857733730491826187>",
      "<:bs_campfire:857733730491826190>",
      "<:firefly_bs:957733730491826190>",
      "<:firework_shelly:957733730491826188>",
      "<:shellyfire:957733730491826189>",
      "<:firedog:757733730491826193>",
      "<:firecat:757733730491826192>",
      "<:bigfire:757733730491826191>",
    ])
    expect(found[8]).toBe("🔥")
  })

  it("answer a name several seen emoji share with the most used", () => {
    expect(emojiNamed("kekw")).toBe("<:KEKW:757733730491826195>")
  })

  it("write the bot's application emoji in by name", () => {
    expect(emojiNamed("SHELLYFIRE")).toBe("<:shellyfire:957733730491826189>")
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

describe("suggestions opened before the server's own emoji arrive", () => {
  it("are asked again once they are there", async () => {
    vi.mocked(listServerEmoji).mockResolvedValue(null)
    await loadServerEmoji()
    let arrive: (listed: DiscordEmojiResponse[]) => void = () => {}
    vi.mocked(listServerEmoji).mockReturnValue(new Promise(resolve => {
      arrive = resolve
    }))
    const view = new EditorView({
      parent: document.body,
      state: EditorState.create({
        doc: ":pogg",
        selection: EditorSelection.cursor(5),
        extensions: autocompletion({override: [emojiCompletion]}),
      }),
    })
    views.push(view)

    const arriving = emojiOnArrival(view)
    startCompletion(view)
    await vi.waitFor(() => expect(completionStatus(view.state)).toBeNull())
    expect(currentCompletions(view.state)).toEqual([])

    arrive(OWN)
    await arriving
    await vi.waitFor(() => expect(currentCompletions(view.state).map(one => one.label)).toContain(":POGGERS:"))
  })

  it("replace a list shown with the standard emoji alone", async () => {
    vi.mocked(listServerEmoji).mockResolvedValue(null)
    await loadServerEmoji()
    let arrive: (listed: DiscordEmojiResponse[]) => void = () => {}
    vi.mocked(listServerEmoji).mockReturnValue(new Promise(resolve => {
      arrive = resolve
    }))
    const view = new EditorView({
      parent: document.body,
      state: EditorState.create({
        doc: ":fir",
        selection: EditorSelection.cursor(4),
        extensions: autocompletion({override: [emojiCompletion]}),
      }),
    })
    views.push(view)

    const arriving = emojiOnArrival(view)
    startCompletion(view)
    await vi.waitFor(() => expect(currentCompletions(view.state)[0]?.apply).toBe("🔥"))

    arrive(OWN)
    await arriving
    await vi.waitFor(() => expect(currentCompletions(view.state)[0]?.apply).toBe("<a:fire:857733730491826187>"))
  })

  it("are left alone where no emoji name is being typed", async () => {
    vi.mocked(listServerEmoji).mockResolvedValue(OWN)
    const view = new EditorView({
      parent: document.body,
      state: EditorState.create({doc: "we won", extensions: autocompletion({override: [emojiCompletion]})}),
    })
    views.push(view)

    await emojiOnArrival(view)

    expect(currentCompletions(view.state)).toEqual([])
  })
})

/* Pasted as a browser pastes: the text passes the editor's clipboard filters before it goes in. */
const paste = (doc: string, text: string) => {
  const view = open(doc, doc.length)
  const filtered = view.state.facet(EditorView.clipboardInputFilter).reduce((said, filter) => filter(said, view.state), text)
  view.dispatch({changes: {from: doc.length, insert: filtered}, userEvent: "input.paste"})
  return view.state.doc.toString()
}

describe("a paste", () => {
  it("writes every name an emoji answers to as that emoji", () => {
    expect(paste("", "see :poggers: by the :fire: at :spaghetti:"))
      .toBe("see <:POGGERS:657733730491826186> by the <a:fire:857733730491826187> at \u{1F35D}")
  })

  it("keeps names no emoji answers to, and times", () => {
    expect(paste("", "at 12:30:45, :notanemoji:")).toBe("at 12:30:45, :notanemoji:")
  })

  it("keeps names in code, in addresses and in what is already written", () => {
    expect(shortcodesWritten("`:fire:` and\n```\n:fire:\n```\n<https://x.nl/:fire:/> <:fire:857733730491826187> <t:1700000000:R>"))
      .toBe("`:fire:` and\n```\n:fire:\n```\n<https://x.nl/:fire:/> <:fire:857733730491826187> <t:1700000000:R>")
  })

  it("keeps every name where it lands in code", () => {
    expect(paste("```\n", ":fire:")).toBe("```\n:fire:")
  })
})
