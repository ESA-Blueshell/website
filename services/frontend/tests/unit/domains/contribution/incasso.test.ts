import {describe, expect, it} from "vitest"
import {dayName, defaultStatementText, incassoFileName, leftOutGroups, maskedIban, renamedForIng} from "@/domains/contribution/incasso"
import {refusable} from "@/domains/contribution/refusals"
import {IncassoLeftOut} from "@/services/api"

describe("an incasso run", () => {
  it("masks an account to its last four, names a day and drafts the statement text", () => {
    expect(maskedIban({ibanCountry: "NL", ibanLastTwo: "34"})).toBe("NL•• … ••34")
    expect(maskedIban({ibanCountry: "DE", ibanLastTwo: null})).toBe("None recorded")
    expect(maskedIban(null, "none")).toBe("none")
    expect(dayName("2026-11-01")).toBe("1 Nov 2026")
    expect(dayName(null)).toBe("—")
    expect(defaultStatementText("2026-09-01", "2027-08-31")).toBe("Contributie 2026-2027 ESA Blueshell")
    expect(incassoFileName("2026-11-01", 1, 1)).toBe("incassobatch-2026-11-01.xlsx")
    expect(incassoFileName("2026-11-01", 2, 3)).toBe("incassobatch-2026-11-01-2-of-3.xlsx")
  })

  it("groups who is left out by why, and lists names ING spells differently", () => {
    const left = (name: string, leftOut: IncassoLeftOut | null) => ({
      userId: 1, name, ingName: name, memberSince: "2025-09-01", leftOut,
    })
    expect(leftOutGroups([left("Ann", IncassoLeftOut.ALREADY_PAID), left("Bea", IncassoLeftOut.NO_BANK_DETAILS), left("Cas", IncassoLeftOut.NO_BANK_DETAILS), left("Dirk", null)]))
      .toEqual([{reason: IncassoLeftOut.NO_BANK_DETAILS, names: ["Bea", "Cas"]}, {reason: IncassoLeftOut.ALREADY_PAID, names: ["Ann"]}])
    expect(renamedForIng([{name: "Zoë", ingName: "Zoe"}, {name: "Ann", ingName: "Ann"}])).toEqual([{name: "Zoë", ingName: "Zoe"}])
  })

  it("says why a run was refused", async () => {
    const reason = async (error: Record<string, unknown>) => {
      const answered = await refusable(Promise.resolve({error, data: undefined}), "fallback")
      return answered.ok ? "" : answered.reason
    }
    expect(await reason({code: "NothingToCollect"})).toContain("at least one member")
    expect(await reason({code: "NotCollectable", userIds: [3, 4]})).toContain("2 of the members")
    expect(await reason({code: "NotCollectable"})).toContain("Some of the members")
    expect(await reason({code: "CollectionDateOutsidePeriod"})).toContain("contribution period")
    expect(await reason({code: "StatementTextMissing"})).toContain("bank statement")
    expect(await reason({code: "StatementTextTooLong", max: 140})).toContain("at most 140")
    expect(await reason({code: "StatementTextTooLong"})).toContain("at most 140")
    expect(await reason({code: "IncassoRunNotFound"})).toContain("no such incasso")
    expect(await reason({code: "IncassoRunSubmitted"})).toContain("already in ING")
    expect(await reason({code: "IngDetailsMissing"})).toContain("incassant ID")
    expect(await reason({code: "MandateChanged", userIds: [3]})).toContain("1 of the members")
    expect(await reason({code: "MandateChanged"})).toContain("Some of the members")
    expect(await reason({code: "IncassoFilePartNotFound"})).toContain("no such file")
  })
})
