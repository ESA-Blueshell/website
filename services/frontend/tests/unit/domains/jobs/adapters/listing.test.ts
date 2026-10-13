import {describe, expect, it, vi} from "vitest"
import {loadJobPage} from "@/domains/jobs/adapters/jobs"
import {list} from "@/services/api"
import {answer} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  list: vi.fn(),
}))

describe("loadJobPage", () => {
  const sortAsked = () => vi.mocked(list).mock.lastCall?.[0]?.query?.sort

  it("asks for the newest first until the reader orders by a column", async () => {
    vi.mocked(list).mockResolvedValue(answer(list, {content: []}))

    await loadJobPage({page: 0, size: 50, search: undefined})
    expect(sortAsked()).toEqual(["updatedAt,desc", "id,desc"])

    await loadJobPage({page: 0, size: 50, search: undefined, sort: {key: "queued", descending: false}})
    expect(sortAsked()).toEqual(["queuedAt,asc", "id,desc"])

    await loadJobPage({page: 0, size: 50, search: undefined, sort: {key: "status", descending: true}})
    expect(sortAsked()).toEqual(["status,desc", "id,desc"])
  })

  it("keeps its own order for a column the api cannot order by", async () => {
    vi.mocked(list).mockResolvedValue(answer(list, {content: []}))

    await loadJobPage({page: 0, size: 50, search: undefined, sort: {key: "kind", descending: false}})

    expect(sortAsked()).toEqual(["updatedAt,desc", "id,desc"])
  })
})
