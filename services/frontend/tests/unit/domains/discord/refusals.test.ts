import {describe, expect, it} from "vitest"
import {reasonFor} from "@/domains/discord/refusals"

describe("what Discord's refusals say", () => {
  it("names the cohort a role already belongs to, and why the board's committee gets none", () => {
    expect(reasonFor({code: "TargetLinkedElsewhere", cohort: "Board"}, "fallback"))
      .toBe("That role already belongs to Board. Unlink it there first, or move it here.")
    expect(reasonFor({code: "BoardCommitteeHasNoRole"}, "fallback"))
      .toBe("The board's committee has no Discord role: the board in office holds @Board.")
  })
})
