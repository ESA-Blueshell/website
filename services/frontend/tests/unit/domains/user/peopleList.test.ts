import {describe, expect, it} from "vitest"
import {MemberType, Role} from "@/services/api"
import {filterPeople, fold, membershipStateOf, peopleRows, sortPeople, type PersonRow} from "@/domains/user"
import {aCommittee, aMembership, aUser} from "../../helpers/apiFixtures"

const row = (id: number, fullName: string, memberSince: string | null): PersonRow =>
  ({id, fullName, username: "", email: "", role: "", membership: "never", type: null, memberSince, needs: [], haystack: fold(fullName)})

describe("the people list", () => {
  it("folds case, accents and punctuation the same way on both sides of a search", () => {
    expect(fold("Zoë O'Brien-Smith")).toBe("zoe o brien smith")
    expect(filterPeople([row(1, "Zoë", null)], {search: "ZOE!", membership: null, type: null, needs: null})).toHaveLength(1)
  })

  it("sorts by name either way, and puts people never a member last by date", () => {
    const rows = [row(1, "Bea", "2020-01-01"), row(2, "Ann", null), row(3, "Cas", "2019-05-01")]

    expect(sortPeople(rows, "name", true).map((one) => one.id)).toEqual([3, 1, 2])
    expect(sortPeople(rows, "memberSince", false).map((one) => one.id)).toEqual([3, 1, 2])
  })

  it("takes the type of the latest membership and the earliest start", () => {
    const [person] = peopleRows(
      [aUser({id: 1, fullName: "Ann", roles: [Role.MEMBER], locked: false, twoFactorOn: true, discordId: null, addressId: null})],
      [
        aMembership({id: 1, userId: 1, startDate: "2019-01-01", endDate: "2020-01-01", memberType: MemberType.REGULAR}),
        aMembership({id: 2, userId: 1, startDate: "2022-01-01", endDate: null, memberType: MemberType.HONORARY}),
      ],
      [],
      [aCommittee({id: 1, name: "Board", members: null})],
    )

    expect(person).toMatchObject({membership: "current", type: MemberType.HONORARY, memberSince: "2019-01-01", needs: ["no-discord", "no-address"]})
  })
  it("calls a running membership pending until its first contribution, and sorts and filters on it", () => {
    expect(membershipStateOf([{endDate: null, pending: true}])).toBe("pending")
    expect(membershipStateOf([{endDate: null, pending: true}, {endDate: null, pending: false}])).toBe("current")
    expect(membershipStateOf([{endDate: "2020-01-01", pending: false}])).toBe("former")
    expect(membershipStateOf([])).toBe("never")

    const rows = [{...row(1, "Ann", null), membership: "former" as const}, {...row(2, "Bea", null), membership: "pending" as const}]
    expect(sortPeople(rows, "membership", false).map((one) => one.id)).toEqual([2, 1])
    expect(filterPeople(rows, {search: "", membership: "pending", type: null, needs: null}).map((one) => one.id)).toEqual([2])
  })
})
