import {describe, expect, it, vi} from "vitest"
import {saveCommittee, saveNewCommittee} from "@/domains/committees/adapters/committees"
import {createCommittee, updateCommittee} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  createCommittee: vi.fn(),
  updateCommittee: vi.fn(),
}))

describe("saveNewCommittee", () => {
  it("records the committee and answers with what was written", async () => {
    vi.mocked(createCommittee).mockResolvedValue({data: {id: 5, name: "Events"}} as never)

    await expect(saveNewCommittee({name: "Events"} as never)).resolves.toEqual({id: 5, name: "Events"})
    expect(createCommittee).toHaveBeenCalledWith({body: {name: "Events"}, throwOnError: true})
  })
})

describe("saveCommittee", () => {
  it("records the change against the committee the number names", async () => {
    vi.mocked(updateCommittee).mockResolvedValue({data: {id: 5, name: "Events"}} as never)

    await expect(saveCommittee(5, {name: "Events"} as never)).resolves.toEqual({id: 5, name: "Events"})
    expect(updateCommittee).toHaveBeenCalledWith({
      path: {id: 5},
      body: {name: "Events"},
      throwOnError: true,
    })
  })
})
