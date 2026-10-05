import {describe, expect, it} from "vitest"
import {MemberType, Role} from "@/services/api"
import {filterPeople, fold, membershipRank, membershipStateOf, peopleRows, type PersonRow} from "@/domains/user"
import {aCommittee, aMembership, aUser} from "../../helpers/apiFixtures"

const row = (id: number, fullName: string, memberSince: string | null): PersonRow =>
  ({id, fullName, username: "", email: "", role: "", membership: "never", type: null, memberSince, needs: [], haystack: fold(fullName)})

describe("the people list", () => {
  it("folds case, accents and punctuation the same way on both sides of a search", () => {
    expect(fold("Zoë O'Brien-Smith")).toBe("zoe o brien smith")
    expect(filterPeople([row(1, "Zoë", null)], {search: "ZOE!", membership: null, type: null, needs: null})).toHaveLength(1)
  })

  it("takes the type of the latest membership and the earliest start", () => {
    const [person] = peopleRows(
      [aUser({id: 1, fullName: "Ann", roles: [Role.MEMBER], locked: false, twoFactorOn: true, discordId: null, addressId: null})],
      [
        aMembership({id: 1, userId: 1, startDate: "2019-01-01", endDate: "2020-01-01", memberType: MemberType.REGULAR}),
        aMembership({id: 2, userId: 1, startDate: "2022-01-01", endDate: null, memberType: MemberType.HONORARY}),
      ],
      [aCommittee({id: 1, name: "Board", members: null})],
    )

    expect(person).toMatchObject({membership: "current", type: MemberType.HONORARY, memberSince: "2019-01-01", needs: ["no-discord", "no-address"]})
    expect(person).toMatchObject({committees: [], discord: null, discordId: null})
  })

  it("carries the committees a person sits on and the Discord name they gave", () => {
    const [person] = peopleRows(
      [aUser({id: 1, fullName: "Ann", discord: "ann#0001"})],
      [],
      [aCommittee({id: 1, name: "Sitecie", members: [{committeeId: 1, userId: 1, createdAt: "", updatedAt: "", version: 0}]})],
    )

    expect(person).toMatchObject({committees: [{name: "Sitecie", slug: expect.any(String)}], discord: "ann#0001"})
  })
  it("calls a running membership pending until its first contribution, and sorts and filters on it", () => {
    expect(membershipStateOf([{endDate: null, pending: true}])).toBe("pending")
    expect(membershipStateOf([{endDate: null, pending: true}, {endDate: null, pending: false}])).toBe("current")
    expect(membershipStateOf([{endDate: "2020-01-01", pending: false}])).toBe("former")
    expect(membershipStateOf([])).toBe("never")

    const rows = [{...row(1, "Ann", null), membership: "former" as const}, {...row(2, "Bea", null), membership: "pending" as const}]
    expect(rows.map(membershipRank)).toEqual([2, 1])
    expect(filterPeople(rows, {search: "", membership: "pending", type: null, needs: null}).map((one) => one.id)).toEqual([2])
  })
})
