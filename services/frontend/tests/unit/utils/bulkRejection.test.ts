import {describe, expect, it} from "vitest"
import {BulkRejectionCode, parseBulkRejection} from "@/utils/bulkRejection"

const refusal = (errors: unknown[]) => ({
  response: {status: 409},
  error: {status: 409, detail: "The selection no longer matches the current data.", errors},
})

describe("parseBulkRejection", () => {
  it("reads the reason and the ids it names", () => {
    const rejection = parseBulkRejection(
      refusal([
        {
          objectName: "BulkMarkPaidRequest",
          field: "userIds",
          code: BulkRejectionCode.deletedUsers,
          message: "1 of the selected users have been deleted.",
          values: [42],
        },
      ]),
    )

    expect(rejection?.reasons).toHaveLength(1)
    expect(rejection?.reasons[0]?.code).toBe("DeletedUserIds")
    expect(rejection?.namedUserIds).toEqual([42])
  })

  it("keeps every reason, so a partly wrong selection is fully explained", () => {
    const rejection = parseBulkRejection(
      refusal([
        {field: "userIds", code: BulkRejectionCode.deletedUsers, message: "", values: [42]},
        {field: "userIds", code: BulkRejectionCode.honoraryUsers, message: "", values: [7, 9]},
      ]),
    )

    expect(rejection?.reasons.map((r) => r.code)).toEqual(["DeletedUserIds", "HonoraryUserIds"])
    expect(rejection?.namedUserIds).toEqual([42, 7, 9])
  })

  it("asks for a reload when the table is out of date", () => {
    const rejection = parseBulkRejection(
      refusal([{field: "userIds", code: BulkRejectionCode.unknownUsers, message: "", values: [1]}]),
    )

    expect(rejection?.requiresReload).toBe(true)
  })

  it("does not ask for a reload when only the choice was wrong", () => {
    const rejection = parseBulkRejection(
      refusal([{field: "userIds", code: BulkRejectionCode.honoraryUsers, message: "", values: [7]}]),
    )

    expect(rejection?.requiresReload).toBe(false)
  })

  it("de-duplicates an id named by more than one reason", () => {
    const rejection = parseBulkRejection(
      refusal([
        {field: "userIds", code: BulkRejectionCode.deletedUsers, message: "", values: [5]},
        {field: "userIds", code: BulkRejectionCode.honoraryUsers, message: "", values: [5]},
      ]),
    )

    expect(rejection?.namedUserIds).toEqual([5])
  })

  it("ignores a reason with no ids, such as a missing period", () => {
    const rejection = parseBulkRejection(
      refusal([
        {field: "contributionPeriodId", code: BulkRejectionCode.unknownPeriod, message: "", values: [100]},
      ]),
    )

    expect(rejection?.reasons[0]?.field).toBe("contributionPeriodId")
    expect(rejection?.requiresReload).toBe(true)
  })

  it("returns null for a status that is not a refusal", () => {
    const errors = [{field: "userIds", code: BulkRejectionCode.unknownUsers, message: "", values: [1]}]
    expect(parseBulkRejection({response: {status: 500}, error: {errors}})).toBeNull()
    expect(parseBulkRejection({response: {status: 200}, error: {errors}})).toBeNull()
  })

  it("returns null when a refusal carries no usable reasons", () => {
    expect(parseBulkRejection(refusal([]))).toBeNull()
    expect(parseBulkRejection({response: {status: 409}, error: "boom"})).toBeNull()
  })

  // Bean validation and the rules above it answer 400 in the same envelope as the 409.
  it("reads a field refused with a 400, and says which status it came with", () => {
    const rejection = parseBulkRejection({
      response: {status: 400},
      error: {
        errors: [{
          objectName: "SendPaymentEmailsRequest",
          field: "paymentDueDate",
          code: BulkRejectionCode.dateOutsidePeriod,
          message: "A date must fall within the contribution period, or shortly after it ends.",
        }],
      },
    })

    expect(rejection?.status).toBe(400)
    expect(rejection?.reasons[0]?.field).toBe("paymentDueDate")
    expect(rejection?.namedUserIds).toEqual([])
  })

  it("composes the sentence for a new code rather than repeating the api's", () => {
    const rejection = parseBulkRejection(
      refusal([{
        field: "forciblyIncludedUserIds",
        code: BulkRejectionCode.nonRecipientForced,
        message: "whatever the api happened to write",
        values: [3],
      }]),
    )

    expect(rejection?.reasons[0]?.message)
      .toBe("Some of the members ticked back in are ones this send does not write to.")
  })

  it("keeps the api's own sentence for a code that composes one", () => {
    const rejection = parseBulkRejection(
      refusal([{
        field: "userIds",
        code: BulkRejectionCode.unknownUsers,
        message: "2 of the selected users no longer exist.",
        values: [8, 9],
      }]),
    )

    expect(rejection?.reasons[0]?.message).toBe("2 of the selected users no longer exist.")
  })
})
