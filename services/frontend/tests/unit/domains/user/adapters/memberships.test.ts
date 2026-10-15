import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  deleteOneMembership,
  endOneMembership,
  listDeletedMembershipsFor,
  listMembershipsFor,
  listOwnMemberships,
  recordTypeOn,
  removeTypeFrom,
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
  findOwnMemberships,
  recordMembershipType,
  removeMembershipType,
  reopenMembership,
  restoreMembership,
} from "@/services/api"
import {MemberType} from "@/services/api"
import {aMembership} from "../../../helpers/apiFixtures"
import {answer, emptyAnswer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  boardCreateMembership: vi.fn(),
  deleteMembership: vi.fn(),
  endMembership: vi.fn(),
  findDeletedMemberships: vi.fn(),
  findMemberships: vi.fn(),
  findOwnMemberships: vi.fn(),
  recordMembershipType: vi.fn(),
  removeMembershipType: vi.fn(),
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
    const terms = {userId: 7, memberType: MemberType.REGULAR, startDate: "2026-09-01"}

    await expect(startMembershipAsBoard(7, terms)).resolves.toEqual(aMembership({id: 33, userId: 7}))
    expect(boardCreateMembership).toHaveBeenCalledWith({
      path: {userId: 7},
      body: terms,
      throwOnError: true,
    })
  })
})

describe("listOwnMemberships", () => {
  it("reads the reader's own memberships, and none when they cannot be read", async () => {
    vi.mocked(findOwnMemberships).mockResolvedValueOnce(answer(findOwnMemberships, [aMembership()]))
    await expect(listOwnMemberships()).resolves.toEqual([aMembership()])

    vi.mocked(findOwnMemberships).mockResolvedValueOnce(refusal(findOwnMemberships, null, 401))
    await expect(listOwnMemberships()).resolves.toEqual([])
  })
})

describe("a membership's types", () => {
  const body = {type: MemberType.ALUMNI, since: "2025-09-01"}

  it("records and removes a type, answering with the membership", async () => {
    const held = aMembership({id: 9, memberType: MemberType.ALUMNI})
    vi.mocked(recordMembershipType).mockResolvedValue(answer(recordMembershipType, held))
    vi.mocked(removeMembershipType).mockResolvedValue(answer(removeMembershipType, held))

    await expect(recordTypeOn(9, body)).resolves.toEqual({ok: true, saved: held})
    await expect(removeTypeFrom(9, 2)).resolves.toEqual({ok: true, saved: held})
    expect(recordMembershipType).toHaveBeenCalledWith({path: {id: 9}, body})
    expect(removeMembershipType).toHaveBeenCalledWith({path: {id: 9, typeId: 2}})
  })

  it("says in words why a type was refused", async () => {
    vi.mocked(recordMembershipType).mockResolvedValueOnce(
      refusal(recordMembershipType, {code: "MembershipTypeOutsideMembership", startDate: "2022-09-01", endDate: "2025-09-01"}),
    )
    await expect(recordTypeOn(9, body)).resolves.toEqual({
      ok: false,
      reason: "Pick a day from 1 September 2022 to the day before 1 September 2025.",
    })

    vi.mocked(recordMembershipType).mockResolvedValueOnce(refusal(recordMembershipType, {code: "MembershipTypeOutsideMembership", startDate: "2022-09-01"}))
    await expect(recordTypeOn(9, body)).resolves.toMatchObject({reason: "Pick a day on or after 1 September 2022."})

    vi.mocked(removeMembershipType).mockResolvedValueOnce(refusal(removeMembershipType, {code: "FirstMembershipTypeStartsTheMembership"}))
    await expect(removeTypeFrom(9, 1)).resolves.toMatchObject({reason: expect.stringContaining("always starts with the membership")})

    vi.mocked(removeMembershipType).mockResolvedValueOnce(refusal(removeMembershipType, {code: "MembershipTypeNotFound"}, 404))
    await expect(removeTypeFrom(9, 5)).resolves.toMatchObject({reason: "That membership type is gone. Reload the page."})
  })
})
