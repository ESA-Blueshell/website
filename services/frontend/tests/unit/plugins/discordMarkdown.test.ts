import {describe, expect, it} from "vitest"
import $markdownToHtml from "@/plugins/markdownToHtml"
import {discordLines} from "@/plugins/discordMarkdown"
import {emojiFile, emojiSrc} from "@/plugins/emojiArt"

const read = (source: string) => $markdownToHtml(source).trim()

describe("a description read the way Discord reads it", () => {
  it("bolds a span that ends in a space, as Discord does", () => {
    expect(read("**Sign ups required! **")).toBe("<p><strong>Sign ups required! </strong></p>")
  })

  it("underlines between double underscores, and italicises between single ones", () => {
    expect(read("__under__ and _slanted_")).toBe("<p><u>under</u> and <em>slanted</em></p>")
  })

  it("leaves underscores inside a word alone", () => {
    expect(read("snake_case_name")).toBe("<p>snake_case_name</p>")
    expect(read("a_b_")).toBe("<p>a_b_</p>")
  })

  it("reads three stars as bold around italic", () => {
    expect(read("***both***")).toBe("<p><strong><em>both</em></strong></p>")
  })

  it("strikes through only between double tildes", () => {
    expect(read("~~gone~~ but ~kept~")).toBe("<p><del>gone</del> but ~kept~</p>")
  })

  it("hides a spoiler behind something to press", () => {
    const shown = document.createElement("div")
    shown.innerHTML = read("the end: ||he wins||")
    const hidden = shown.querySelector(".spoiler") as HTMLElement

    expect(hidden.textContent).toBe("he wins")
    expect(hidden.getAttribute("role")).toBe("button")
    expect(hidden.getAttribute("tabindex")).toBe("0")
    expect(hidden.getAttribute("aria-expanded")).toBe("false")
  })

  it("sets subtext small, with its own formatting", () => {
    expect(read("above\n-# small **print**")).toBe(
      "<p>above</p>\n<p class=\"subtext\">small <strong>print</strong></p>")
  })

  it("quotes everything after three angle brackets", () => {
    expect(discordLines("intro\n>>> one\ntwo")).toBe("intro\n> one\n> two")
  })

  it("ends a list at the first line that is not an item", () => {
    expect(discordLines("- one\n- two\nafter")).toBe("- one\n- two\n\nafter")
    expect(discordLines("- one\n  under one\nafter")).toBe("- one\n  under one\n\nafter")
    expect(read("- one\nafter")).toBe("<ul>\n<li>one</li>\n</ul>\n<p>after</p>")
  })

  it("leaves lines inside a code block as they are", () => {
    expect(discordLines("```\n- one\nafter\n>>> not a quote\n```")).toBe("```\n- one\nafter\n>>> not a quote\n```")
  })

  it("keeps tables and pictures, which Discord shows as typed", () => {
    expect(read("| a |\n| - |\n| b |")).toContain("<table>")
    expect(read("![map](https://x.io/map.png)")).toContain("<img src=\"https://x.io/map.png\" alt=\"map\">")
  })

  it("sanitises what it is handed", () => {
    const html = read("<script>alert('x')</script><p>safe</p>")
    expect(html).not.toContain("<script>")
    expect(html).toContain("safe")
  })
})

describe("emoji in a description", () => {
  it("draws a standard emoji as its Noto picture", () => {
    const shown = document.createElement("div")
    shown.innerHTML = read("hot 🔥")
    const drawn = shown.querySelector("img.emoji") as HTMLImageElement

    expect(drawn.getAttribute("src")).toBe("/emoji/1f525.svg")
    expect(drawn.alt).toBe("🔥")
  })

  it("draws a shortcode written before they became emoji as they were typed", () => {
    expect(read(":rocket:")).toContain("src=\"/emoji/1f680.svg\"")
  })

  it("never draws one inside code or an address", () => {
    const html = read("`:fire: 🔥` and https://x.io/a:fire:b")
    expect(html).toContain("<code>:fire: 🔥</code>")
    expect(html).toContain(">https://x.io/a:fire:b</a>")
    expect(html).not.toContain("<img")
  })

  it("leaves a time that looks like a shortcode alone", () => {
    expect(read("20:00-22:00")).toBe("<p>20:00-22:00</p>")
  })

  it("leaves characters that are text by default as text", () => {
    expect(read("© ™ ↔")).toBe("<p>© ™ ↔</p>")
  })

  it("names the file by code point, without the variation selector", () => {
    expect(emojiFile("❤️")).toBe("2764")
    expect(emojiFile("1️⃣")).toBe("0031-20e3")
    expect(emojiFile("🇳🇱")).toBe("1f1f3-1f1f1")
    expect(emojiFile("👍🏽")).toBe("1f44d-1f3fd")
    expect(emojiSrc("🍝")).toBe("/emoji/1f35d.svg")
  })
})
