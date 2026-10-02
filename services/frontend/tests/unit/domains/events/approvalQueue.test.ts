import {describe, expect, it, vi} from "vitest"
import {readApprovalQueue} from "@/domains/events/adapters/approvalQueue"
import {changesSaid} from "@/domains/events/island/approvalWords"
import {EventField, listApprovalQueue} from "@/services/api"
import {answer, refusal} from "../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  listApprovalQueue: vi.fn(),
}))

describe("the approval queue", () => {
  it("reads the queue, or nothing where it cannot be read", async () => {
    vi.mocked(listApprovalQueue).mockResolvedValue(answer(listApprovalQueue, []))
    expect(await readApprovalQueue()).toEqual([])
    vi.mocked(listApprovalQueue).mockResolvedValue(refusal(listApprovalQueue, null, 403))
    expect(await readApprovalQueue()).toBeNull()
  })

  it("says what a re-approval changed in plain words", () => {
    expect(changesSaid([])).toBe("Changed since it was approved")
    expect(changesSaid([EventField.TITLE])).toBe("Changed: title")
    expect(changesSaid([EventField.TITLE, EventField.TIMES, EventField.SIGN_UP])).toBe("Changed: title, times and sign-ups")
    expect(changesSaid([EventField.DESCRIPTION, EventField.LOCATION, EventField.PRICES, EventField.MEMBERS_ONLY, EventField.COMMITTEE]))
      .toBe("Changed: description, location, prices, who may come and committee")
  })
})
