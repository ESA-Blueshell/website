import {describe, expect, it, vi} from "vitest"
import {deleteCommittee, listCommittees, listMyCommittees} from "@/domains/committees/adapters/committees"
import {deleteCommitteeById, findCommittees, findCommitteesByUserId} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCommittees: vi.fn(),
  findCommitteesByUserId: vi.fn(),
  deleteCommitteeById: vi.fn(),
}))

describe("listCommittees", () => {
  it("answers with the committees it read", async () => {
    vi.mocked(findCommittees).mockResolvedValue({data: [{id: 1, name: "SiteCie"}]} as never)

    await expect(listCommittees()).resolves.toEqual([{id: 1, name: "SiteCie"}])
  })

  it("reads an answer without a body as no committees", async () => {
    vi.mocked(findCommittees).mockResolvedValue({} as never)

    await expect(listCommittees()).resolves.toEqual([])
  })

  it("throws on a refusal rather than answering with an empty listing", async () => {
    vi.mocked(findCommittees).mockRejectedValue(new Error("refused"))

    await expect(listCommittees()).rejects.toBeDefined()
  })
})

describe("listMyCommittees", () => {
  it("answers with the committees the account belongs to", async () => {
    vi.mocked(findCommitteesByUserId).mockResolvedValue({data: [{id: 2, name: "EventCie"}]} as never)

    await expect(listMyCommittees()).resolves.toEqual([{id: 2, name: "EventCie"}])
  })

  it("reads an answer without a body as no committees", async () => {
    vi.mocked(findCommitteesByUserId).mockResolvedValue({} as never)

    await expect(listMyCommittees()).resolves.toEqual([])
  })

  it("throws on a refusal, like the listing beside it", async () => {
    vi.mocked(findCommitteesByUserId).mockRejectedValue(new Error("refused"))

    await expect(listMyCommittees()).rejects.toBeDefined()
  })
})

describe("deleteCommittee", () => {
  it("addresses the committee by its number", async () => {
    vi.mocked(deleteCommitteeById).mockResolvedValue({} as never)

    await deleteCommittee(5)

    expect(deleteCommitteeById).toHaveBeenCalledWith({path: {id: 5}, throwOnError: true})
  })
})
