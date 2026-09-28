import {describe, expect, it, vi} from "vitest"
import {findMemberAccounts, loadMemberAccounts, searchMemberAccounts} from "@/domains/user/adapters/users"
import {findUsers} from "@/services/api"
import type {UserDetailResponse} from "@/services/api"
import {answer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findUsers: vi.fn(),
}))

/**
 * A page of accounts. The rows are loose on purpose: the loader guards against rows missing the
 * id, name or address their type promises, and these tests hand it exactly those.
 */
const page = (rows: Array<Partial<Record<keyof UserDetailResponse, unknown>>>) =>
  answer(findUsers, {content: rows as UserDetailResponse[]})

describe("loadMemberAccounts", () => {
  it("asks for the whole listing rather than a page whose size it would have to guess", async () => {
    vi.mocked(findUsers).mockResolvedValue(page([]))

    await loadMemberAccounts()

    expect(findUsers).toHaveBeenCalledWith({})
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

  it("answers with no accounts where the api sent none", async () => {
    vi.mocked(findUsers).mockResolvedValue(page([]))

    await expect(loadMemberAccounts()).resolves.toEqual([])
  })

  // The sdk resolves rather than throws on 4xx/5xx, so a refused read has to be told apart from
  // an empty one here or a picker tells a board member that nobody here has an account.
  it("answers with nothing at all where the read failed", async () => {
    vi.mocked(findUsers).mockResolvedValue(refusal(findUsers, {status: 500}))

    await expect(loadMemberAccounts()).resolves.toBeNull()
  })

  it("answers with nothing at all where the api sent a body with no page in it", async () => {
    vi.mocked(findUsers).mockResolvedValue(answer(findUsers, {}))

    await expect(loadMemberAccounts()).resolves.toBeNull()
  })
})

describe("searching accounts", () => {
  it("asks for one page of what was typed, and tells a refusal from nobody found", async () => {
    vi.mocked(findUsers)
      .mockResolvedValueOnce(page([{id: 1, fullName: "Roos Kruk"}]))
      .mockResolvedValueOnce(refusal(findUsers, {}))
      .mockResolvedValueOnce(refusal(findUsers, {}))
      .mockResolvedValueOnce(answer(findUsers, {}))

    await expect(findMemberAccounts("roos", 20)).resolves.toEqual([{id: 1, fullName: "Roos Kruk"}])
    expect(findUsers).toHaveBeenLastCalledWith({query: {search: "roos", page: 0, size: 20}})
    await expect(findMemberAccounts("roos", 20)).resolves.toBeNull()
    await expect(searchMemberAccounts("roos", 20)).resolves.toEqual([])
    await expect(searchMemberAccounts("roos", 20)).resolves.toEqual([])
  })
})
