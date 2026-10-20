import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import DeclarationSheet from "@/domains/declarations/island/DeclarationSheet.vue"
import {BookingKind, type Declaration, type DeclarationStep} from "@/domains/declarations/declarations"

const step = (state: DeclarationStep["state"], reason?: string): DeclarationStep =>
  ({state, at: "2026-10-02T10:00:00Z", by: "Ann Vos", ...(reason ? {reason} : {})})
const receipt = {id: 1, path: "1.webp", name: "a", type: "image/webp", url: "/r/1"}

const sheet = (declaration: Partial<Declaration>, payTo: unknown = null) => mount(DeclarationSheet, {props: {
  declaration: {
    submitterId: 12,
    submitter: "Ann Vos",
    confirmed: false,
    state: "DRAFT",
    steps: [],
    receipts: [],
    expenses: [{id: "1", paidOn: "2026-10-01", description: "Snacks", amount: "48.35", for: {kind: BookingKind.EVENT, id: 7, name: "LAN"}, receipts: [receipt, receipt]}],
    ...declaration,
  },
  payTo,
}})

describe("the declaration sheet", () => {
  it("lays a draft out as the paper form, with the account it is paid to", () => {
    const draft = sheet({}, {holder: "A. Vos", iban: "NL •• … ••00", bic: "ABNANL2A"})

    expect(draft.text()).toContain("A. Vos")
    expect(draft.text()).toContain("ABNANL2A")
    expect(draft.text()).toContain("2026-10-01")
    expect(draft.text()).toContain("2 receipts")
    expect(draft.text()).toContain("€ 48.35")
    expect(draft.text()).toContain("Not submitted yet.")
    expect(draft.text()).toContain("Not approved yet.")
    expect(sheet({confirmed: true, expenses: [], submitter: ""}).text()).toContain("Ticked. Not submitted yet.")
    expect(sheet({expenses: []}).text()).toContain("No expenses yet.")
    expect(sheet({receipts: [receipt]}).text()).toContain("1 receipt for the whole declaration")
    expect(sheet({receipts: [receipt, receipt]}).text()).toContain("2 receipts for the whole declaration")
    expect(sheet({expenses: [{id: "1", paidOn: "", description: "", amount: "", for: null, receipts: []}]}).text()).toContain("Not added")
  })

  it("says what the board decided, and when", () => {
    expect(sheet({state: "SUBMITTED", steps: [step("SUBMITTED")]}).text()).toContain("Submitted on 2 October 2026 by Ann Vos")
    expect(sheet({state: "REJECTED", steps: [step("REJECTED", "Not ours.")]}).text()).toContain("Rejected on 2 October 2026 by Ann Vos: Not ours.")
    expect(sheet({state: "DRAFT", steps: [step("DRAFT", "Missing receipt.")]}).text()).toContain("Sent back on 2 October 2026 by Ann Vos: Missing receipt.")
    expect(sheet({state: "APPROVED", steps: [step("APPROVED")]}).text()).toContain("Approved on 2 October 2026 by Ann Vos.")
    expect(sheet({state: "PAID", steps: [step("APPROVED"), step("PAID")]}).text()).toContain("Paid on 2 October 2026.")
  })
})
