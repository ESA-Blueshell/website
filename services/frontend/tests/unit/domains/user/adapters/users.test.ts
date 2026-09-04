import {describe, expect, it, vi} from "vitest"
import {loadMemberAccounts} from "@/domains/user/adapters/users"
import {findUsers} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findUsers: vi.fn(),
}))

const page = (content: unknown[]) => ({data: {content}}) as never

describe("loadMemberAccounts", () => {
  it("asks for one page big enough to filter where it is used", async () => {
    vi.mocked(findUsers).mockResolvedValue(page([]))

    await loadMemberAccounts()

    expect(findUsers).toHaveBeenCalledWith({query: {size: 500}})
  })

  it("names an account by the full name on it", async () => {
    vi.mocked(findUsers).mockResolvedValue(page([{id: 1, fullName: "Roos Kruk", email: "roos@esa.test"}]))

    await expect(loadMemberAccounts()).resolves.toEqual([
      {id: 1, name: "Roos Kruk", email: "roos@esa.test"},
    ])
  })

  // An account exists from the moment somebody is invited, so the name can be missing while
  // the address is the only thing anybody could pick it out by.
  it("falls back to the address, and then to the account's number", async () => {
    vi.mocked(findUsers).mockResolvedValue(page([
      {id: 1, email: "roos@esa.test"},
      {id: 2},
    ]))

    await expect(loadMemberAccounts()).resolves.toEqual([
      {id: 2, name: "Member 2", email: null},
      {id: 1, name: "roos@esa.test", email: "roos@esa.test"},
    ])
  })

  it("reads an absent address as no address rather than as an empty one", async () => {
    vi.mocked(findUsers).mockResolvedValue(page([{id: 1, fullName: "Roos Kruk", email: null}]))

    await expect(loadMemberAccounts()).resolves.toEqual([{id: 1, name: "Roos Kruk", email: null}])
  })

  // The id is what attaching a membership writes down, so a row without one is not an account
  // anything can be attached to.
  it("leaves out a row the api gave no id for", async () => {
    vi.mocked(findUsers).mockResolvedValue(page([{id: null, fullName: "Nobody"}, {fullName: "Nor them"}]))

    await expect(loadMemberAccounts()).resolves.toEqual([])
  })

  it("answers in name order, which is the order the picker reads them in", async () => {
    vi.mocked(findUsers).mockResolvedValue(page([
      {id: 1, fullName: "Wouter Bos"},
      {id: 2, fullName: "Anne de Vries"},
      {id: 3, fullName: "roos kruk"},
    ]))

    await expect(loadMemberAccounts()).resolves.toMatchObject([
      {name: "Anne de Vries"},
      {name: "roos kruk"},
      {name: "Wouter Bos"},
    ])
  })

  it("answers with no accounts where the api sent none, and where the read failed", async () => {
    vi.mocked(findUsers).mockResolvedValue(page([]))
    await expect(loadMemberAccounts()).resolves.toEqual([])

    vi.mocked(findUsers).mockResolvedValue({error: {status: 500}, data: undefined} as never)
    await expect(loadMemberAccounts()).resolves.toEqual([])
  })
})
