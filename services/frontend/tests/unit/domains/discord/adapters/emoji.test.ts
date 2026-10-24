import {describe, expect, it, vi} from "vitest"
import {listServerEmoji} from "@/domains/discord/adapters/emoji"
import {listDiscordEmojis} from "@/services/api"
import {DiscordEmojiKind} from "@/services/api/blueshell/types.gen"
import {answer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", () => ({listDiscordEmojis: vi.fn()}))

describe("listServerEmoji", () => {
  it("answers the server's own emoji, or nothing where the api cannot ask", async () => {
    vi.mocked(listDiscordEmojis).mockResolvedValue(answer(listDiscordEmojis, [{id: "657", name: "ShellyStar", animated: false, kind: DiscordEmojiKind.SERVER}]))
    expect(await listServerEmoji()).toEqual([{id: "657", name: "ShellyStar", animated: false, kind: DiscordEmojiKind.SERVER}])

    vi.mocked(listDiscordEmojis).mockResolvedValue(refusal(listDiscordEmojis, {status: 503}))
    expect(await listServerEmoji()).toBeNull()
  })
})
