import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import MandatePanel from "@/components/management/MandatePanel.vue"
import {IncassoStanding, MandateKind} from "@/services/api"
import {settle} from "../../helpers/testUtils"

const api = vi.hoisted(() => ({findMandate: vi.fn(), recordMandate: vi.fn(), revealIban: vi.fn(), downloadMandatePdf: vi.fn()}))

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
    await wrapper.get('[data-testid="mandate-iban"] input').setValue("NL91 ABNA 0417 1643 00")
    await wrapper.get('[data-testid="mandate-holder"] input').setValue("Ann Vos")
    await wrapper.findComponent({name: "DateInput"}).vm.$emit("update:modelValue", "2026-09-01")
    await wrapper.get('[data-testid="mandate-form"]').trigger("submit")
    await settle()

    expect(api.recordMandate).toHaveBeenCalledWith({path: {membershipId: 9}, body: {iban: "NL91 ABNA 0417 1643 00", accountHolder: "Ann Vos", signedOn: "2026-09-01", replacesOnline: false}})
    expect(wrapper.get('[data-testid="mandate-standing"]').text()).toBe("Collected by incasso")
    expect(wrapper.get('[data-testid="mandate-facts"]').text()).toContain("NL•• … ••00")
    expect(wrapper.text()).not.toContain("0417")
    expect(wrapper.emitted("changed")).toHaveLength(1)
    expect(wrapper.get('[data-testid="mandate-record"]').text()).toBe("Replace the IBAN and mandate")
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

  it("says a wiped mandate's bank details are gone, and offers no reveal", async () => {
    api.findMandate.mockResolvedValue({status: 200, data: {...recorded, standing: IncassoStanding.NONE, accountHolder: null, bankDetailsWiped: true}})
    const wrapper = mount(MandatePanel, {props: {membershipId: 9}})
    await settle()

    expect(wrapper.get('[data-testid="mandate-wiped"]').text()).toContain("wiped 13 months after the last collection")
    expect(wrapper.find('[data-testid="mandate-reveal"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="mandate-account"]').text()).toBe("NL•• … ••00")
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

  it("says what kind of mandate it is, and replaces an online one only once that is confirmed", async () => {
    api.findMandate.mockResolvedValue({status: 200, data: {...recorded, kind: MandateKind.PAPER}})
    const paper = mount(MandatePanel, {props: {membershipId: 9}})
    await settle()
    expect(paper.get('[data-testid="mandate-kind"]').text()).toMatch(/^Paper mandate, recorded on .+\. The signed paper is the record, so there is no PDF\.$/)
    expect(paper.find('[data-testid="mandate-pdf"]').exists()).toBe(false)
    await paper.get('[data-testid="mandate-record"]').trigger("click")
    expect(paper.find('[data-testid="mandate-replaces-online"]').exists()).toBe(false)

    api.findMandate.mockResolvedValue({status: 200, data: {...recorded, kind: MandateKind.ONLINE, authorisedAt: "2026-09-30T10:00:00Z"}})
    const wrapper = mount(MandatePanel, {props: {membershipId: 9}})
    await settle()
    expect(wrapper.get('[data-testid="mandate-kind"]').text()).toContain("Online mandate, authorised on")

    await wrapper.get('[data-testid="mandate-record"]').trigger("click")
    expect(wrapper.get('[data-testid="mandate-replaces-online"]').text()).toContain("Its PDF will no longer be available.")
    expect(wrapper.get('[data-testid="mandate-save"]').attributes("disabled")).toBeDefined()

    await wrapper.get('[data-testid="mandate-replaces-online-confirm"]').setValue(true)
    expect(wrapper.get('[data-testid="mandate-save"]').attributes("disabled")).toBeUndefined()
    await wrapper.get('[data-testid="mandate-form"]').trigger("submit")
    await settle()
    expect(api.recordMandate.mock.calls[0]![0].body.replacesOnline).toBe(true)
  })

  it("downloads an online mandate's PDF, says who recorded a paper one, and says why a download was refused", async () => {
    api.findMandate.mockResolvedValue({status: 200, data: {...recorded, kind: MandateKind.PAPER, recordedByName: "Bo Ard"}})
    const paper = mount(MandatePanel, {props: {membershipId: 9}})
    await settle()
    expect(paper.get('[data-testid="mandate-kind"]').text()).toContain("Paper mandate, recorded by Bo Ard on")

    api.findMandate.mockResolvedValue({status: 200, data: {...recorded, kind: MandateKind.ONLINE, authorisedAt: "2026-09-30T10:00:00Z"}})
    api.downloadMandatePdf.mockResolvedValue({status: 200, data: new Blob(["%PDF"])})
    const created = vi.fn(() => "blob:mandate")
    const revoked = vi.fn()
    vi.stubGlobal("URL", {...URL, createObjectURL: created, revokeObjectURL: revoked})
    const wrapper = mount(MandatePanel, {props: {membershipId: 9}})
    await settle()

    await wrapper.get('[data-testid="mandate-pdf"]').trigger("click")
    await settle()
    expect(api.downloadMandatePdf).toHaveBeenCalledWith({path: {membershipId: 9}})
    expect(created).toHaveBeenCalled()
    expect(revoked).toHaveBeenCalledWith("blob:mandate")

    api.downloadMandatePdf.mockResolvedValue({status: 404, error: {code: "NoOnlineMandate"}})
    await wrapper.get('[data-testid="mandate-pdf"]').trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="mandate-pdf-failure"]').text()).toContain("authorised on the site")
    vi.unstubAllGlobals()
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
      ["MandateAddressMissing", "whole address"], ["MandateWordingOutdated", "Reload the page"],
    ] as const
    for (const [code, words] of refusals) {
      api.recordMandate.mockResolvedValue({status: 400, error: {code, detail: code}})
      await wrapper.get('[data-testid="mandate-form"]').trigger("submit")
      await settle()
      expect(wrapper.get('[data-testid="mandate-failure"]').text()).toContain(words)
    }
    api.recordMandate.mockResolvedValue({status: 409, error: {code: "ReplacesOnlineMandate", authorisedAt: "2026-09-30T10:00:00Z"}})
    await wrapper.get('[data-testid="mandate-form"]').trigger("submit")
    await settle()
    expect(wrapper.get('[data-testid="mandate-failure"]').text()).toContain("authorised on 30 September 2026")
    api.recordMandate.mockResolvedValue({status: 409, error: {code: "ReplacesOnlineMandate"}})
    await wrapper.get('[data-testid="mandate-form"]').trigger("submit")
    await settle()
    expect(wrapper.get('[data-testid="mandate-failure"]').text()).toContain("authorised on an earlier day")

    await wrapper.get('[data-testid="mandate-cancel"]').trigger("click")
    expect(wrapper.find('[data-testid="mandate-form"]').exists()).toBe(false)
  })
})
