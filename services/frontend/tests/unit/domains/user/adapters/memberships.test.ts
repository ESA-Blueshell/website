import {describe, expect, it, vi} from "vitest"
import {
  deleteOneMembership,
  endOneMembership,
  listDeletedMembershipsFor,
  listMembershipsFor,
  reopenOneMembership,
  restoreOneMembership,
  startMembershipAsBoard,
} from "@/domains/user/adapters/memberships"
import {
  boardCreateMembership,
  deleteMembership,
  endMembership,
  findDeletedMemberships,
  findMemberships,
  reopenMembership,
  restoreMembership,
} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  boardCreateMembership: vi.fn(),
  deleteMembership: vi.fn(),
  endMembership: vi.fn(),
  findDeletedMemberships: vi.fn(),
  findMemberships: vi.fn(),
  reopenMembership: vi.fn(),
  restoreMembership: vi.fn(),
}))

describe("listMembershipsFor", () => {
  it("asks for the memberships of the one account", async () => {
    vi.mocked(findMemberships).mockResolvedValue({data: [{id: 1}]} as never)

    await expect(listMembershipsFor(42)).resolves.toEqual([{id: 1}])
    expect(findMemberships).toHaveBeenCalledWith({query: {userId: 42}, throwOnError: true})
  })

  it("reads an answer without a body as no memberships", async () => {
    vi.mocked(findMemberships).mockResolvedValue({} as never)

    await expect(listMembershipsFor(42)).resolves.toEqual([])
  })
})

describe("listDeletedMembershipsFor", () => {
  it("asks for the deleted memberships of the one account", async () => {
    vi.mocked(findDeletedMemberships).mockResolvedValue({data: [{id: 9}]} as never)

    await expect(listDeletedMembershipsFor(42)).resolves.toEqual([{id: 9}])
    expect(findDeletedMemberships).toHaveBeenCalledWith({path: {userId: 42}, throwOnError: true})
  })

  it("reads an answer without a body as no memberships", async () => {
    vi.mocked(findDeletedMemberships).mockResolvedValue({} as never)

    await expect(listDeletedMembershipsFor(42)).resolves.toEqual([])
  })
})

describe("the one-membership writes", () => {
  it.each([
    ["ends", endOneMembership, endMembership],
    ["reopens", reopenOneMembership, reopenMembership],
    ["removes", deleteOneMembership, deleteMembership],
    ["restores", restoreOneMembership, restoreMembership],
  ])("%s the membership the number names, and throws on a refusal", async (_name, call, client) => {
    vi.mocked(client).mockResolvedValue({} as never)

    await call(10)

    expect(client).toHaveBeenCalledWith({path: {id: 10}, throwOnError: true})
  })
})

describe("startMembershipAsBoard", () => {
  it("starts the membership on the account's behalf", async () => {
    vi.mocked(boardCreateMembership).mockResolvedValue({data: {id: 33, userId: 7}} as never)

    await expect(startMembershipAsBoard(7, {userId: 7} as never)).resolves.toEqual({id: 33, userId: 7})
    expect(boardCreateMembership).toHaveBeenCalledWith({
      path: {userId: 7},
      body: {userId: 7},
      throwOnError: true,
    })
  })
})
