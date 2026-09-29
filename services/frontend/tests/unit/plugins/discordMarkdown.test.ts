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

  it("leaves a shortcode as the text it is, as Discord does", () => {
    expect(read(":rocket:")).toBe("<p>:rocket:</p>")
  })

  it("never draws an emoji inside code or an address", () => {
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

  it("draws a server's emoji from Discord, from this server or any other", () => {
    const shown = document.createElement("div")
    shown.innerHTML = read("gg <:POGGERS:657733730491826186> and <a:party:123456789012345678>")
    const [still, moving] = [...shown.querySelectorAll<HTMLImageElement>("img.emoji")]

    expect(still?.getAttribute("src")).toBe("https://cdn.discordapp.com/emojis/657733730491826186.webp?size=48")
    expect(still?.alt).toBe(":POGGERS:")
    expect(moving?.getAttribute("src")).toBe("https://cdn.discordapp.com/emojis/123456789012345678.gif?size=48")
  })

  it("leaves a server's emoji in code as written", () => {
    expect(read("`<:POGGERS:657733730491826186>`")).toBe("<p><code>&lt;:POGGERS:657733730491826186&gt;</code></p>")
  })

})

describe("mentions and timestamps in a description", () => {
  const shown = (source: string) => {
    const page = document.createElement("div")
    page.innerHTML = read(source)
    return page
  }

  it("draws a member, a role and a channel with the ID they are named by", () => {
    const page = shown("<@123456789012345678> <@!123456789012345679> <@&223456789012345678> <#323456789012345678>")

    expect([...page.querySelectorAll("[data-user]")].map(one => (one as HTMLElement).dataset.user))
      .toEqual(["123456789012345678", "123456789012345679"])
    expect(page.querySelector(".mention--role")?.getAttribute("data-role")).toBe("223456789012345678")
    expect(page.querySelector("a")?.getAttribute("href"))
      .toBe("https://discord.com/channels/324285132133629963/323456789012345678")
    expect(page.querySelector("a > .mention")?.getAttribute("data-channel")).toBe("323456789012345678")
  })

  it("draws a timestamp as a time, in the style written or f", () => {
    const page = shown("<t:1790000000:R> and <t:1790000000>")
    const [relative, plain] = [...page.querySelectorAll("time")]

    expect(plain?.getAttribute("datetime")).toBe(new Date(1790000000 * 1000).toISOString())
    expect(plain?.textContent).toBe(new Intl.DateTimeFormat(undefined, {dateStyle: "long", timeStyle: "short"})
      .format(new Date(1790000000 * 1000)))
    expect(relative?.textContent).not.toBe(plain?.textContent)
  })

  it("leaves what only looks like one as written", () => {
    expect(read("<t:soon> <@me> `<@123456789012345678>`")).toBe(
      "<p>&lt;t:soon&gt; &lt;@me&gt; <code>&lt;@123456789012345678&gt;</code></p>")
  })
})

