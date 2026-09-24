import {describe, expect, it} from "vitest"
import {discordChannel, discordInvite} from "@/domains/discord/adapters/doors"

describe("the doors into Discord", () => {
  it("leads through the api on the page's own origin, never to an invite code", () => {
    expect(discordInvite("board")).toBe("http://localhost:3000/api/discord/invite/board")
    expect(discordChannel("suggestions")).toBe("http://localhost:3000/api/discord/channel/suggestions")
  })
})
