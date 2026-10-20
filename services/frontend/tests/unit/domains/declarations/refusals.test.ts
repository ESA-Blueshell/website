import {describe, expect, it} from "vitest"
import {reasonFor} from "@/domains/declarations/refusals"

describe("a refused declaration", () => {
  it("says in the reader's words why each refusal happened", () => {
    const said = (code: string) => reasonFor({code}, "fallback")

    expect(said("DeclarationNotFound")).toBe("This declaration does not exist, or it is not yours.")
    expect(said("DeclarationNotDraft")).toContain("Withdraw it to make changes.")
    expect(said("DeclarationNotSubmitted")).toContain("so you cannot withdraw it")
    expect(said("NoBankAccount")).toBe("Add your bank account before you submit.")
    expect(said("NoReceipt")).toBe("Add at least one receipt.")
    expect(said("NotConfirmed")).toBe("Tick the box to confirm.")
    expect(said("NoExpense")).toBe("Add at least one expense.")
    expect(reasonFor({code: "ExpenseIncomplete", expense: 1, field: "paidOn"}, "x")).toBe("Expense 1 needs the date you paid.")
    expect(reasonFor({code: "ExpenseIncomplete", expense: 1, field: "description"}, "x")).toBe("Expense 1 needs a description.")
    expect(said("UnknownReceipt")).toBe("One of the receipts could not be found. Add it again.")
    expect(said("ReasonMissing")).toBe("Write the reason. The person who submitted it gets it by email.")
    expect(reasonFor({code: "DeclarationStateChanged", state: "DRAFT"}, "x")).toBe("This declaration is a draft again. Reload the page.")
    expect(said("DeclarationStateChanged")).toBe("This declaration has changed. Reload the page.")
    expect(said("Unheard")).toBe("fallback")
  })
})
