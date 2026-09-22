import {describe, expect, it, vi} from "vitest"
import {readEvent} from "@/domains/events/adapters/events"
import {findEventById} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findEventById: vi.fn(),
}))

describe("readEvent", () => {
  it("answers with the event behind the number", async () => {
    vi.mocked(findEventById).mockResolvedValue({data: {id: 33, title: "Hackathon"}} as never)

    await expect(readEvent(33)).resolves.toEqual({id: 33, title: "Hackathon"})
  })

  it("throws on a refusal rather than answering with no event", async () => {
    vi.mocked(findEventById).mockRejectedValue(new Error("refused"))

    await expect(readEvent(33)).rejects.toBeDefined()
  })
})
