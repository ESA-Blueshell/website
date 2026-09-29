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
import type {CreateContributionPeriodRequest} from "@/services/api"
import {aContributionPeriod} from "../../../helpers/apiFixtures"
import {answer, emptyAnswer} from "../../../helpers/sdkAnswers"

const terms: CreateContributionPeriodRequest = {
  startDate: "2026-01-01",
  endDate: "2026-12-31",
  halfYearCutoffDate: "2026-07-01",
  fullYearFee: 20,
  halfYearFee: 12,
  alumniFee: 10,
}

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
    vi.mocked(findCurrentContributionPeriod).mockResolvedValue(
      answer(findCurrentContributionPeriod, aContributionPeriod({id: 1})),
    )

    await expect(readCurrentPeriod()).resolves.toMatchObject({id: 1})
  })

  it("answers with nothing where there is no open period", async () => {
    vi.mocked(findCurrentContributionPeriod).mockResolvedValue(emptyAnswer(findCurrentContributionPeriod))

    await expect(readCurrentPeriod()).resolves.toBeNull()
  })

  it("throws on a refusal rather than answering with no period", async () => {
    vi.mocked(findCurrentContributionPeriod).mockRejectedValue(new Error("refused"))

    await expect(readCurrentPeriod()).rejects.toBeDefined()
  })
})

describe("listPeriods", () => {
  it("answers with every period on file", async () => {
    vi.mocked(findContributionPeriods).mockResolvedValue(
      answer(findContributionPeriods, [aContributionPeriod({id: 1}), aContributionPeriod({id: 2})]),
    )

    await expect(listPeriods()).resolves.toHaveLength(2)
  })

  it("answers with an empty listing where the api named no periods", async () => {
    vi.mocked(findContributionPeriods).mockResolvedValue(emptyAnswer(findContributionPeriods))

    await expect(listPeriods()).resolves.toEqual([])
  })

  it("throws on a refusal rather than answering with no periods", async () => {
    vi.mocked(findContributionPeriods).mockRejectedValue(new Error("refused"))

    await expect(listPeriods()).rejects.toBeDefined()
  })
})

describe("saveNewPeriod", () => {
  it("answers with the recorded period", async () => {
    vi.mocked(createContributionPeriod).mockResolvedValue(answer(createContributionPeriod, aContributionPeriod({id: 11})))

    await expect(saveNewPeriod(terms)).resolves.toMatchObject({id: 11})
    expect(createContributionPeriod).toHaveBeenCalledWith({
      body: terms,
      throwOnError: true,
    })
  })
})

describe("savePeriod", () => {
  it("names the period on the path and answers with the change", async () => {
    vi.mocked(updateContributionPeriod).mockResolvedValue(answer(updateContributionPeriod, aContributionPeriod({id: 22})))

    await expect(savePeriod(22, {...terms, version: 1})).resolves.toMatchObject({id: 22})
    expect(updateContributionPeriod).toHaveBeenCalledWith({
      path: {id: 22},
      body: {...terms, version: 1},
      throwOnError: true,
    })
  })
})

describe("deletePeriod", () => {
  it("removes the period", async () => {
    vi.mocked(deleteContributionPeriodById).mockResolvedValue(emptyAnswer(deleteContributionPeriodById))

    await expect(deletePeriod(3)).resolves.toBeUndefined()
    expect(deleteContributionPeriodById).toHaveBeenCalledWith({path: {id: 3}, throwOnError: true})
  })

  it("throws on a refusal so the caller reports it", async () => {
    vi.mocked(deleteContributionPeriodById).mockRejectedValue(new Error("refused"))

    await expect(deletePeriod(3)).rejects.toBeDefined()
  })
})
