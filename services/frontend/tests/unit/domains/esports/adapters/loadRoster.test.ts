import {beforeEach, describe, expect, it, vi} from "vitest"
import {loadRoster} from "@/domains/esports/adapters/esports"
import {findRoster} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findRoster: vi.fn(),
}))

describe("loadRoster", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("answers with the entries it read", async () => {
    vi.mocked(findRoster).mockResolvedValue({data: [{id: 1, handle: "nova", role: "PLAYER", sortIndex: 0}]} as never)

    await expect(loadRoster(7, "VAL", 3)).resolves.toHaveLength(1)
  })

  it("answers with an empty roster where the api said there is nobody", async () => {
    vi.mocked(findRoster).mockResolvedValue({data: []} as never)

    await expect(loadRoster(7, "VAL", 3)).resolves.toEqual([])
  })

  // The sdk resolves rather than throws on 4xx/5xx, so a refused read has to be told apart
  // from an empty one here or the editor saves emptiness over a real squad.
  it("answers with nothing at all where the read failed", async () => {
    vi.mocked(findRoster).mockResolvedValue({error: {status: 500}, data: undefined} as never)

    await expect(loadRoster(7, "VAL", 3)).resolves.toBeNull()
  })
})
