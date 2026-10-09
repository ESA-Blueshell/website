import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {
  DescriptionCellAlign,
  type DescriptionNode,
  DescriptionNodeKind as Kind,
  DescriptionTimeStyle,
} from "@/services/api"
import {DescriptionNodes, mentionsOf, type NameOf} from "@/domains/discord/descriptionNodes"

/* A node with only what the drawing reads; where it was read from is the drawing's least concern. */
const n = (kind: Kind, over: Partial<DescriptionNode> = {}, ...children: DescriptionNode[]): DescriptionNode =>
  ({kind, start: 0, end: 0, children, ...over})
const text = (said: string) => n(Kind.TEXT, {text: said})

const unnamed: NameOf = () => undefined
const drawn = (nodes: DescriptionNode[], nameOf: NameOf = unnamed) =>
  mount(DescriptionNodes, {props: {nodes, nameOf}}).html()

describe("a description drawn from its tree", () => {
  it("draws formatting as its elements", () => {
    expect(drawn([n(Kind.PARAGRAPH, {},
      n(Kind.STRONG, {}, text("b")), n(Kind.EMPHASIS, {}, text("i")), n(Kind.UNDERLINE, {}, text("u")),
      n(Kind.STRIKE, {}, text("s")), n(Kind.CODE, {text: "c"}), n(Kind.LINE_BREAK))]))
      .toBe("<p><strong>b</strong><em>i</em><u>u</u><del>s</del><code>c</code><br></p>")
  })

  it("draws headings, subtext, quotes, rules and code blocks", () => {
    const html = drawn([
      n(Kind.HEADING, {level: 2}, text("Rules")),
      n(Kind.SUBTEXT, {}, text("small")),
      n(Kind.QUOTE, {}, n(Kind.PARAGRAPH, {}, text("said"))),
      n(Kind.RULE),
      n(Kind.CODE_BLOCK, {text: "x = 1\n", language: "kotlin"}),
      n(Kind.CODE_BLOCK, {text: "plain"}),
    ])

    expect(html).toContain("<h2>Rules</h2>")
    expect(html).toContain("<p class=\"subtext\">small</p>")
    expect(html).toContain("<blockquote>\n  <p>said</p>\n</blockquote>")
    expect(html).toContain("<hr>")
    expect(html).toContain("<pre><code class=\"language-kotlin\">x = 1</code></pre>")
    expect(html).toContain("<pre><code>plain</code></pre>")
  })

  it("draws a tight list's items as lines, a loose one's as paragraphs, and counts from where it starts", () => {
    const item = n(Kind.ITEM, {}, n(Kind.PARAGRAPH, {}, text("one")))

    expect(drawn([n(Kind.LIST, {ordered: false, tight: true}, item)])).toBe("<ul>\n  <li>one</li>\n</ul>")
    expect(drawn([n(Kind.LIST, {ordered: true, number: 3, tight: false}, item)]))
      .toBe("<ol start=\"3\">\n  <li>\n    <p>one</p>\n  </li>\n</ol>")
    expect(drawn([n(Kind.LIST, {ordered: true, number: 1, tight: true}, item)])).toBe("<ol>\n  <li>one</li>\n</ol>")
  })

  it("draws a table with its head apart and its cells aligned", () => {
    const html = drawn([n(Kind.TABLE, {},
      n(Kind.ROW, {}, n(Kind.CELL, {header: true, align: DescriptionCellAlign.CENTER}, text("a"))),
      n(Kind.ROW, {}, n(Kind.CELL, {header: false}, text("b"))))])

    expect(html).toContain("<thead>")
    expect(html).toContain("<th align=\"center\">a</th>")
    expect(html).toContain("<tbody>")
    expect(html).toContain("<td>b</td>")
  })

  it("draws links and pictures", () => {
    const html = drawn([n(Kind.PARAGRAPH, {},
      n(Kind.LINK, {href: "https://x.io", title: "home"}, text("site")),
      n(Kind.IMAGE, {href: "https://x.io/map.png", text: "map"}))])

    expect(html).toContain("<a href=\"https://x.io\" title=\"home\">site</a>")
    expect(html).toContain("<img src=\"https://x.io/map.png\" alt=\"map\">")
  })

  it("hides a spoiler behind something to press", () => {
    expect(drawn([n(Kind.SPOILER, {}, text("he wins"))]))
      .toBe("<span class=\"spoiler\" role=\"button\" tabindex=\"0\" aria-expanded=\"false\">he wins</span>")
  })

  it("draws an emoji whose picture will not load as what it stands for", async () => {
    const wrapper = mount(DescriptionNodes, {props: {
      nodes: [n(Kind.SERVER_EMOJI, {name: "party", id: "123456789012345678", animated: false})],
      nameOf: unnamed,
    }})

    await wrapper.get("img").trigger("error")

    expect(wrapper.html()).toBe(":party:")
  })

  it("draws standard and server emoji as their pictures", () => {
    const html = drawn([
      n(Kind.EMOJI, {text: "🔥"}),
      n(Kind.SERVER_EMOJI, {name: "party", id: "123456789012345678", animated: true}),
    ])

    expect(html).toContain("<img class=\"emoji\" src=\"/emoji/1f525.svg\" alt=\"🔥\" draggable=\"false\">")
    expect(html).toContain("src=\"https://cdn.discordapp.com/emojis/123456789012345678.gif?size=48\" alt=\":party:\"")
  })

  it("draws a timestamp as a time, in the style it names", () => {
    const html = drawn([n(Kind.TIMESTAMP, {unix: 1790000000, style: DescriptionTimeStyle.LONG_DATE})])

    expect(html).toContain(`datetime="${new Date(1790000000 * 1000).toISOString()}"`)
    expect(html).toContain(new Intl.DateTimeFormat(undefined, {dateStyle: "long"}).format(new Date(1790000000 * 1000)))
  })

  it("draws mentions with a stand-in until they are named, and in their role's colour once they are", () => {
    const mentions = [
      n(Kind.USER_MENTION, {id: "1"}),
      n(Kind.ROLE_MENTION, {id: "2"}),
      n(Kind.CHANNEL_MENTION, {id: "3"}),
    ]

    expect(drawn(mentions)).toBe(
      "<span class=\"mention\" data-user=\"1\">@…</span>\n"
      + "<span class=\"mention mention--role\" data-role=\"2\">@…</span>\n"
      + "<a href=\"https://discord.com/channels/324285132133629963/3\"><span class=\"mention\" data-channel=\"3\">#…</span></a>")
    const named = drawn(mentions, (kind, id) => (kind === "role" ? {said: "@Board", colour: "#ff0000"} : {said: `${kind}${id}`}))
    expect(named).toContain("style=\"--mention: #ff0000;\">@Board</span>")
    expect(named).toContain(">user1</span>")
  })
})

describe("the mentions in a tree", () => {
  it("are gathered by kind, however deep they stand", () => {
    expect(mentionsOf([n(Kind.PARAGRAPH, {}, n(Kind.USER_MENTION, {id: "1"}),
      n(Kind.STRONG, {}, n(Kind.ROLE_MENTION, {id: "2"}), n(Kind.CHANNEL_MENTION, {id: "3"})))]))
      .toEqual({users: ["1"], roles: ["2"], channels: ["3"]})
  })

  it("are asked about once each, however often they are written", () => {
    expect(mentionsOf([n(Kind.ROLE_MENTION, {id: "2"}), n(Kind.ROLE_MENTION, {id: "2"})]))
      .toEqual({users: [], roles: ["2"], channels: []})
  })
})
