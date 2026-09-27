import {beforeEach, describe, expect, it, vi} from "vitest"
import {fillMentions, forgetMentionNames, nameMentions} from "@/domains/discord"
import {readMentionNames} from "@/domains/discord/adapters/mentions"

vi.mock("@/domains/discord/adapters/mentions", () => ({readMentionNames: vi.fn(), listServerChannels: vi.fn()}))

const NAMED = {
  users: [{id: "11", name: "Anna"}],
  roles: [{id: "901", name: "Gamers", colour: 0x3498DB}, {id: "324", name: "@everyone"}],
  channels: [{id: "2", name: "events-info"}],
}

beforeEach(() => {
  forgetMentionNames()
  vi.mocked(readMentionNames).mockResolvedValue(NAMED as never)
})

describe("the names behind a description's mentions", () => {
  it("names members, roles and channels as Discord shows them", async () => {
    const nameOf = await nameMentions({users: ["11"], roles: ["901", "324"], channels: ["2"]})

    expect(nameOf("user", "11")).toEqual({said: "@Anna"})
    expect(nameOf("role", "901")).toEqual({said: "@Gamers", colour: "#3498db"})
    expect(nameOf("role", "324")).toEqual({said: "@everyone"})
    expect(nameOf("channel", "2")).toEqual({said: "#events-info"})
  })

  it("names what the server lacks as Discord does, and asks the api once for it", async () => {
    await nameMentions({users: ["99"], roles: ["98"], channels: ["97"]})
    const nameOf = await nameMentions({users: ["99"], roles: ["98"], channels: ["97"]})

    expect([nameOf("user", "99"), nameOf("role", "98"), nameOf("channel", "97")].map(one => one.said))
      .toEqual(["@unknown-user", "@deleted-role", "#unknown"])
    expect(readMentionNames).toHaveBeenCalledTimes(1)
  })

  it("keeps nothing where the api cannot ask, so the next page asks again", async () => {
    vi.mocked(readMentionNames).mockResolvedValueOnce(null)
    expect((await nameMentions({users: ["11"], roles: [], channels: []}))("user", "11").said).toBe("@unknown-user")

    expect((await nameMentions({users: ["11"], roles: [], channels: []}))("user", "11").said).toBe("@Anna")
  })

  it("names every mention drawn in a page, and colours a role's", async () => {
    const page = document.createElement("div")
    page.innerHTML = "<span data-user=\"11\">@…</span><span data-role=\"901\">@…</span><span data-channel=\"2\">#…</span>"

    await fillMentions(page)

    expect(page.textContent).toBe("@Anna@Gamers#events-info")
    expect((page.querySelector("[data-role]") as HTMLElement).style.getPropertyValue("--mention")).toBe("#3498db")
  })

  it("asks nothing for a page with no mentions", async () => {
    await fillMentions(document.createElement("div"))

    expect(readMentionNames).not.toHaveBeenCalled()
  })
})
