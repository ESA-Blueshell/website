import {describe, expect, it} from "vitest"
import {
  centsOf,
  type Declaration,
  type DeclarationStep,
  type Expense,
  metaOf,
  receiptsOf,
  sentBack,
  stateOf,
  stepLabel,
  titleOf,
  totalOf,
} from "@/domains/declarations/declarations"

const receipt = (id: number) => ({id, path: `${id}.webp`, name: "bill.webp", type: "image/webp", url: `/r/${id}`})
const expense = (description: string, amount: string, receipts = [receipt(1)]): Expense =>
  ({id: description, paidOn: "2026-10-01", description, amount, for: null, receipts})
const step = (state: DeclarationStep["state"], at: string, reason?: string): DeclarationStep =>
  ({state, at, by: "Ann Vos", ...(reason ? {reason} : {})})

const declaration = (over: Partial<Declaration> = {}): Declaration => ({
  id: 4,
  submitter: "Ann Vos",
  state: "DRAFT",
  expenses: [expense("Pizza", "12.50")],
  receipts: [],
  confirmed: false,
  steps: [step("DRAFT", "2026-10-01T10:00:00Z")],
  createdAt: "2026-10-01T10:00:00Z",
  ...over,
})

describe("a declaration", () => {
  it("adds its expenses up in cents, whatever the money field holds", () => {
    expect(centsOf("12.505")).toBe(1251)
    expect(centsOf("")).toBeNull()
    expect(totalOf([expense("a", "12.50"), expense("b", "0.1"), expense("c", "")])).toBe(12.6)
  })

  it("is named by its first expense and summed up by its count, total and latest date", () => {
    expect(titleOf(declaration({expenses: []}))).toBe("New declaration")
    expect(titleOf(declaration())).toBe("Pizza")
    expect(titleOf(declaration({expenses: [expense("Pizza", "1"), expense("Cola", "2")]}))).toBe("Pizza and 1 more")

    expect(metaOf(declaration())).toBe("1 expense · € 12.50 · Started 1 October 2026")
    const submitted = declaration({state: "SUBMITTED", steps: [step("DRAFT", "2026-10-01T10:00:00Z"), step("SUBMITTED", "2026-10-02T10:00:00Z")]})
    expect(metaOf(submitted)).toBe("1 expense · € 12.50 · Submitted 2 October 2026")
    const paid = declaration({state: "PAID", expenses: [], steps: [step("PAID", "2026-10-09T10:00:00Z")]})
    expect(metaOf(paid)).toBe("0 expenses · € 0.00 · Paid 9 October 2026")
  })

  it("reads a later draft step as sent back with a reason and as withdrawn without one", () => {
    const back = declaration({steps: [step("DRAFT", "a"), step("SUBMITTED", "b"), step("DRAFT", "c", "The receipt is missing.")]})
    expect(sentBack(back)?.reason).toBe("The receipt is missing.")
    expect(stateOf(back)).toEqual({label: "Sent back", tone: "warn"})
    expect(stateOf(declaration({state: "PAID"}))).toEqual({label: "Paid", tone: "ok"})
    expect(sentBack(declaration())).toBeNull()

    expect(back.steps.map(stepLabel)).toEqual(["Started", "Submitted", "Sent back"])
    expect(stepLabel(step("DRAFT", "d"), 3)).toBe("Withdrawn")
  })

  it("holds the receipts of its expenses and those for the whole declaration", () => {
    expect(receiptsOf({expenses: [expense("a", "1", [receipt(1)])], receipts: [receipt(2)], confirmed: false}).map(one => one.id))
      .toEqual([1, 2])
  })
})
