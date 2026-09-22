import {describe, expect, it, vi} from "vitest"
import {readCurrentPeriod} from "@/domains/contribution/adapters/periods"
import {findCurrentContributionPeriod} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCurrentContributionPeriod: vi.fn(),
}))

describe("readCurrentPeriod", () => {
  it("answers with the period being charged over", async () => {
    vi.mocked(findCurrentContributionPeriod).mockResolvedValue({
      data: {id: 1, startDate: "2026-01-01", endDate: "2026-12-31"},
    } as never)

    await expect(readCurrentPeriod()).resolves.toMatchObject({id: 1})
  })

  it("answers with nothing where there is no open period", async () => {
    vi.mocked(findCurrentContributionPeriod).mockResolvedValue({} as never)

    await expect(readCurrentPeriod()).resolves.toBeNull()
  })

  it("throws on a refusal rather than answering with no period", async () => {
    vi.mocked(findCurrentContributionPeriod).mockRejectedValue(new Error("refused"))

    await expect(readCurrentPeriod()).rejects.toBeDefined()
  })
})
