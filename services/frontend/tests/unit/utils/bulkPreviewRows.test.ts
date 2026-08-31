import {describe, expect, it} from "vitest"
import {BulkRowDisposition, BulkRowReason, MemberType} from "@/services/api"
import {bulkRowsFromPreview} from "@/utils/bulkPreviewRows"
import {noMembershipTarget, target} from "../helpers/bulkFixtures"

describe("bulkRowsFromPreview", () => {
  it("dresses the api's decision with the member details the table holds", () => {
    const rows = bulkRowsFromPreview(
      [target(1, {name: "Ada Lovelace", memberSince: "2019-09-01"})],
      [{userId: 1, disposition: BulkRowDisposition.INCLUDED}],
    )

    expect(rows).toEqual([
      {
        userId: 1,
        name: "Ada Lovelace",
        disposition: "INCLUDED",
        memberType: MemberType.REGULAR,
        memberSince: "2019-09-01",
      },
    ])
  })

  /**
   * The counterpart of the contribution dialogs' rule: there the column is read for the fee,
   * so it shows the current spell; here it is read for who this person is, so a member who
   * left and came back keeps the day they first joined.
   */
  it("shows the day a returning member first joined, not the spell they are on", () => {
    const rows = bulkRowsFromPreview(
      [
        target(1, {
          memberSince: "2019-09-01",
          mostRecentMembership: {
            type: MemberType.REGULAR,
            startDate: "2025-09-01",
            endDate: null,
            incasso: false,
          },
        }),
      ],
      [{userId: 1, disposition: BulkRowDisposition.SKIPPED, reason: BulkRowReason.ALREADY_ACTIVE}],
    )

    expect(rows[0]!.memberSince).toBe("2019-09-01")
  })

  it("carries the reason through so a skipped row can say why", () => {
    const rows = bulkRowsFromPreview(
      [noMembershipTarget(7)],
      [{userId: 7, disposition: BulkRowDisposition.SKIPPED, reason: BulkRowReason.NO_ACTIVE_MEMBERSHIP}],
    )

    expect(rows[0]!.disposition).toBe("SKIPPED")
    expect(rows[0]!.reason).toBe("NO_ACTIVE_MEMBERSHIP")
    expect(rows[0]!.memberSince).toBeUndefined()
  })

  it("keeps the api's order rather than the table's", () => {
    const rows = bulkRowsFromPreview(
      [target(1), target(2)],
      [
        {userId: 2, disposition: BulkRowDisposition.SKIPPED, reason: BulkRowReason.STARTED_TODAY},
        {userId: 1, disposition: BulkRowDisposition.INCLUDED},
      ],
    )

    expect(rows.map((row) => row.userId)).toEqual([2, 1])
  })

  it("still renders a member the table has since lost, under their id", () => {
    const rows = bulkRowsFromPreview([], [{userId: 42, disposition: BulkRowDisposition.INCLUDED}])

    expect(rows[0]!.name).toBe("#42")
  })
})
