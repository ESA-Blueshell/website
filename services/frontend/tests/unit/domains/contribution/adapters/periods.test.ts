import {describe, expect, it, vi} from "vitest"
import {
  deletePeriod,
  listPeriods,
  readCurrentPeriod,
  saveNewPeriod,
  savePeriod,
} from "@/domains/contribution/adapters/periods"
import {
  createContributionPeriod,
  deleteContributionPeriodById,
  findContributionPeriods,
  findCurrentContributionPeriod,
  updateContributionPeriod,
} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCurrentContributionPeriod: vi.fn(),
  findContributionPeriods: vi.fn(),
  createContributionPeriod: vi.fn(),
  updateContributionPeriod: vi.fn(),
  deleteContributionPeriodById: vi.fn(),
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

describe("listPeriods", () => {
  it("answers with every period on file", async () => {
    vi.mocked(findContributionPeriods).mockResolvedValue({data: [{id: 1}, {id: 2}]} as never)

    await expect(listPeriods()).resolves.toHaveLength(2)
  })

  it("answers with an empty listing where the api named no periods", async () => {
    vi.mocked(findContributionPeriods).mockResolvedValue({} as never)

    await expect(listPeriods()).resolves.toEqual([])
  })

  it("throws on a refusal rather than answering with no periods", async () => {
    vi.mocked(findContributionPeriods).mockRejectedValue(new Error("refused"))

    await expect(listPeriods()).rejects.toBeDefined()
  })
})

describe("saveNewPeriod", () => {
  it("answers with the recorded period", async () => {
    vi.mocked(createContributionPeriod).mockResolvedValue({data: {id: 11}} as never)

    await expect(saveNewPeriod({startDate: "2026-01-01"} as never)).resolves.toMatchObject({id: 11})
    expect(createContributionPeriod).toHaveBeenCalledWith({
      body: {startDate: "2026-01-01"},
      throwOnError: true,
    })
  })
})

describe("savePeriod", () => {
  it("names the period on the path and answers with the change", async () => {
    vi.mocked(updateContributionPeriod).mockResolvedValue({data: {id: 22}} as never)

    await expect(savePeriod(22, {version: 1} as never)).resolves.toMatchObject({id: 22})
    expect(updateContributionPeriod).toHaveBeenCalledWith({
      path: {id: 22},
      body: {version: 1},
      throwOnError: true,
    })
  })
})

describe("deletePeriod", () => {
  it("removes the period", async () => {
    vi.mocked(deleteContributionPeriodById).mockResolvedValue({} as never)

    await expect(deletePeriod(3)).resolves.toBeUndefined()
    expect(deleteContributionPeriodById).toHaveBeenCalledWith({path: {id: 3}, throwOnError: true})
  })

  it("throws on a refusal so the caller reports it", async () => {
    vi.mocked(deleteContributionPeriodById).mockRejectedValue(new Error("refused"))

    await expect(deletePeriod(3)).rejects.toBeDefined()
  })
})
