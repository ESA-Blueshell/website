import {beforeEach, describe, expect, it, vi} from "vitest"
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
import {MemberType} from "@/services/api"
import {aMembership} from "../../../helpers/apiFixtures"
import {answer, emptyAnswer} from "../../../helpers/sdkAnswers"

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
    vi.mocked(findMemberships).mockResolvedValue(answer(findMemberships, [aMembership()]))

    await expect(listMembershipsFor(42)).resolves.toEqual([aMembership()])
    expect(findMemberships).toHaveBeenCalledWith({query: {userId: 42}, throwOnError: true})
  })

  it("reads an answer without a body as no memberships", async () => {
    vi.mocked(findMemberships).mockResolvedValue(emptyAnswer(findMemberships))

    await expect(listMembershipsFor(42)).resolves.toEqual([])
  })
})

describe("listDeletedMembershipsFor", () => {
  it("asks for the deleted memberships of the one account", async () => {
    vi.mocked(findDeletedMemberships).mockResolvedValue(answer(findDeletedMemberships, [aMembership({id: 9})]))

    await expect(listDeletedMembershipsFor(42)).resolves.toEqual([aMembership({id: 9})])
    expect(findDeletedMemberships).toHaveBeenCalledWith({path: {userId: 42}, throwOnError: true})
  })

  it("reads an answer without a body as no memberships", async () => {
    vi.mocked(findDeletedMemberships).mockResolvedValue(emptyAnswer(findDeletedMemberships))

    await expect(listDeletedMembershipsFor(42)).resolves.toEqual([])
  })
})

describe("the one-membership writes", () => {
  beforeEach(() => {
    vi.mocked(endMembership).mockResolvedValue(emptyAnswer(endMembership))
    vi.mocked(reopenMembership).mockResolvedValue(emptyAnswer(reopenMembership))
    vi.mocked(deleteMembership).mockResolvedValue(emptyAnswer(deleteMembership))
    vi.mocked(restoreMembership).mockResolvedValue(emptyAnswer(restoreMembership))
  })

  it.each([
    ["ends", endOneMembership, endMembership],
    ["reopens", reopenOneMembership, reopenMembership],
    ["removes", deleteOneMembership, deleteMembership],
    ["restores", restoreOneMembership, restoreMembership],
  ])("%s the membership the number names, and throws on a refusal", async (_name, call, client) => {
    await call(10)

    expect(client).toHaveBeenCalledWith({path: {id: 10}, throwOnError: true})
  })
})

describe("startMembershipAsBoard", () => {
  it("starts the membership on the account's behalf", async () => {
    vi.mocked(boardCreateMembership).mockResolvedValue(answer(boardCreateMembership, aMembership({id: 33, userId: 7})))
    const terms = {userId: 7, memberType: MemberType.REGULAR, incasso: false, startDate: "2026-09-01"}

    await expect(startMembershipAsBoard(7, terms)).resolves.toEqual(aMembership({id: 33, userId: 7}))
    expect(boardCreateMembership).toHaveBeenCalledWith({
      path: {userId: 7},
      body: terms,
      throwOnError: true,
    })
  })
})
