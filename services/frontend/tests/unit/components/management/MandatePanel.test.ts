import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import MandatePanel from "@/components/management/MandatePanel.vue"
import {IncassoStanding} from "@/services/api"
import {settle} from "../../helpers/testUtils"

const api = vi.hoisted(() => ({findMandate: vi.fn(), recordMandate: vi.fn(), revealIban: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const recorded = {
  membershipId: 9, standing: IncassoStanding.MANDATE_RECORDED, accountHolder: "Ann Vos", ibanCountry: "NL", ibanLastTwo: "00",
  reference: "BLUESHELL-9-20260901", signedOn: "2026-09-01", recordedBy: 3, recordedAt: "2026-09-02T10:00:00Z",
}

describe("the mandate panel", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.findMandate.mockResolvedValue({status: 200, data: {membershipId: 9, standing: IncassoStanding.NONE}})
    api.recordMandate.mockResolvedValue({status: 200, data: recorded})
  })

  it("records a paper mandate and shows only the last four of the account", async () => {
    const wrapper = mount(MandatePanel, {props: {membershipId: 9}})
    await settle()
    expect(wrapper.get('[data-testid="mandate-standing"]').text()).toBe("Pays by transfer")

    await wrapper.get('[data-testid="mandate-record"]').trigger("click")
    const fields = wrapper.findAllComponents({name: "VTextField"})
    await fields[0]!.vm.$emit("update:modelValue", "NL91 ABNA 0417 1643 00")
    await fields[1]!.vm.$emit("update:modelValue", "Ann Vos")
    await fields[2]!.vm.$emit("update:modelValue", "2026-09-01")
    await wrapper.get('[data-testid="mandate-form"]').trigger("submit")
    await settle()

    expect(api.recordMandate).toHaveBeenCalledWith({path: {membershipId: 9}, body: {iban: "NL91 ABNA 0417 1643 00", accountHolder: "Ann Vos", signedOn: "2026-09-01"}})
    expect(wrapper.get('[data-testid="mandate-standing"]').text()).toBe("Collected by incasso")
    expect(wrapper.get('[data-testid="mandate-facts"]').text()).toContain("NL•• … ••00")
    expect(wrapper.text()).not.toContain("0417")
    expect(wrapper.emitted("changed")).toHaveLength(1)
    expect(wrapper.get('[data-testid="mandate-record"]').text()).toBe("Replace the mandate")
  })

  it("shows the masked account alone where the account holder cannot be opened", async () => {
    api.findMandate.mockResolvedValue({status: 200, data: {...recorded, accountHolder: null}})
    const wrapper = mount(MandatePanel, {props: {membershipId: 9}})
    await settle()

    expect(wrapper.get('[data-testid="mandate-account"]').text()).toBe("NL•• … ••00")
  })

  it("reveals the full IBAN on asking, hides it again, and forgets it when the mandate is read anew", async () => {
    api.findMandate.mockResolvedValue({status: 200, data: recorded})
    api.revealIban.mockResolvedValue({status: 200, data: {iban: "NL91ABNA0417164300"}})
    const wrapper = mount(MandatePanel, {props: {membershipId: 9}})
    await settle()
    const account = () => wrapper.get('[data-testid="mandate-account"]').text()
    expect(account()).toBe("NL•• … ••00, Ann Vos")

    await wrapper.get('[data-testid="mandate-reveal"]').trigger("click")
    await settle()
    expect(api.revealIban).toHaveBeenCalledWith({path: {membershipId: 9}})
    expect(account()).toBe("NL91 ABNA 0417 1643 00, Ann Vos")
    expect(wrapper.get('[data-testid="mandate-reveal"]').text()).toBe("Hide the IBAN")

    await wrapper.get('[data-testid="mandate-reveal"]').trigger("click")
    expect(account()).toBe("NL•• … ••00, Ann Vos")

    await wrapper.get('[data-testid="mandate-reveal"]').trigger("click")
    await settle()
    await wrapper.setProps({membershipId: 10})
    await settle()
    expect(account()).not.toContain("0417")
    expect(api.revealIban).toHaveBeenCalledTimes(2)
  })

  it("says why a reveal was refused, and keeps the account masked", async () => {
    api.findMandate.mockResolvedValue({status: 200, data: recorded})
    api.revealIban.mockResolvedValue({status: 503, error: {code: "SealingUnavailable"}})
    const wrapper = mount(MandatePanel, {props: {membershipId: 9}})
    await settle()

    await wrapper.get('[data-testid="mandate-reveal"]').trigger("click")
    await settle()

    expect(wrapper.get('[data-testid="mandate-reveal-failure"]').text()).toContain("Try again in a moment")
    expect(wrapper.get('[data-testid="mandate-account"]').text()).toBe("NL•• … ••00, Ann Vos")

    for (const [code, words] of [["NoMandateRecorded", "No mandate is recorded"], ["BankDetailsUnopenable", "Record the mandate again"]] as const) {
      api.revealIban.mockResolvedValue({status: 409, error: {code}})
      await wrapper.get('[data-testid="mandate-reveal"]').trigger("click")
      await settle()
      expect(wrapper.get('[data-testid="mandate-reveal-failure"]').text()).toContain(words)
    }
  })

  it("says why a mandate was refused, and cancels", async () => {
    api.recordMandate.mockResolvedValue({status: 400, error: {code: "InvalidIban", detail: "That is not a valid IBAN."}})
    api.findMandate.mockResolvedValue({status: 200, data: {membershipId: 9, standing: IncassoStanding.ON_INCASSO_WITHOUT_BANK_DETAILS}})
    const wrapper = mount(MandatePanel, {props: {membershipId: 9}})
    await settle()
    expect(wrapper.get('[data-testid="mandate-standing"]').text()).toContain("no bank details")

    await wrapper.get('[data-testid="mandate-record"]').trigger("click")
    await wrapper.get('[data-testid="mandate-form"]').trigger("submit")
    await settle()
    expect(wrapper.get('[data-testid="mandate-failure"]').text()).toContain("not a valid IBAN")

    const refusals = [
      ["MandateSignedInFuture", "signed today or before"], ["AccountHolderMissing", "whose account"], ["SealingUnavailable", "Try again in a moment"],
    ] as const
    for (const [code, words] of refusals) {
      api.recordMandate.mockResolvedValue({status: 400, error: {code, detail: code}})
      await wrapper.get('[data-testid="mandate-form"]').trigger("submit")
      await settle()
      expect(wrapper.get('[data-testid="mandate-failure"]').text()).toContain(words)
    }

    await wrapper.get('[data-testid="mandate-cancel"]').trigger("click")
    expect(wrapper.find('[data-testid="mandate-form"]').exists()).toBe(false)
  })
})
