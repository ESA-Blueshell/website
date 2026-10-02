import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import IncassoSetUp from "@/components/account/IncassoSetUp.vue"
import {IncassoStanding} from "@/services/api"
import {SIGNUP_TOKEN_HEADER} from "@/plugins/signupContinuation"
import {settle} from "../../helpers/testUtils"

const api = vi.hoisted(() => ({findOwnMandate: vi.fn(), setUpOwnMandate: vi.fn(), setUpMandate: vi.fn(), twoFactorStanding: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const own = {standing: IncassoStanding.MANDATE_RECORDED, ibanCountry: "NL", ibanLastTwo: "00", reference: "BLUESHELL-9-20260930", signedOn: "2026-09-30", pending: false}

const fill = async (wrapper: ReturnType<typeof mount>) => {
  await wrapper.get('[data-testid="incasso-open"]').trigger("click")
  const fields = wrapper.findAllComponents({name: "VTextField"})
  await fields[0]!.vm.$emit("update:modelValue", "NL91 ABNA 0417 1643 00")
  await fields[1]!.vm.$emit("update:modelValue", "Ann Vos")
  await wrapper.findComponent({name: "VCheckbox"}).vm.$emit("update:modelValue", true)
}

describe("setting up incasso", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.findOwnMandate.mockResolvedValue({status: 200, data: {standing: IncassoStanding.NONE, pending: false}})
    api.setUpOwnMandate.mockResolvedValue({status: 200, data: own})
    api.setUpMandate.mockResolvedValue({status: 204, data: undefined})
    api.twoFactorStanding.mockResolvedValue({status: 200, data: {on: false, required: false}})
  })

  it("asks the person to prove it is them when the api wants a step-up, and saves once they have", async () => {
    api.setUpOwnMandate.mockResolvedValueOnce({status: 403, error: {code: "StepUpRequired"}, response: {status: 403}})
    const wrapper = mount(IncassoSetUp, {global: {stubs: {StepUpDialog: {name: "StepUpDialog", props: ["modelValue", "twoFactorOn"], emits: ["proved"], template: "<div />"}}}})
    await settle()
    await fill(wrapper)
    await wrapper.get('[data-testid="incasso-form"]').trigger("submit")
    await settle()

    const dialog = wrapper.getComponent({name: "StepUpDialog"})
    expect(dialog.props("modelValue")).toBe(true)
    expect(dialog.props("twoFactorOn")).toBe(false)
    expect(wrapper.find('[data-testid="incasso-failure"]').exists()).toBe(false)

    dialog.vm.$emit("proved")
    await settle()
    expect(api.setUpOwnMandate).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="incasso-saved"]').text()).toBe("Your bank details are saved.")
  })

  it("saves the member's bank details and shows only the account masked", async () => {
    const wrapper = mount(IncassoSetUp)
    await settle()
    expect(wrapper.find('[data-testid="incasso-none"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="incasso-open"]').text()).toBe("Pay by incasso")

    await fill(wrapper)
    await wrapper.get('[data-testid="incasso-form"]').trigger("submit")
    await settle()

    expect(api.setUpOwnMandate).toHaveBeenCalledWith({body: {iban: "NL91 ABNA 0417 1643 00", accountHolder: "Ann Vos", authorised: true}})
    expect(wrapper.get('[data-testid="incasso-current"]').text()).toContain("from the account NL•• … ••00")
    expect(wrapper.text()).not.toContain("0417")
    expect(wrapper.find('[data-testid="incasso-saved"]').exists()).toBe(true)
    expect(wrapper.emitted("saved")).toHaveLength(1)
    expect(wrapper.get('[data-testid="incasso-open"]').text()).toBe("Change bank details")
  })

  it("says details given ahead of a membership start with it", async () => {
    api.findOwnMandate.mockResolvedValue({status: 200, data: {...own, standing: IncassoStanding.NONE, pending: true}})
    const wrapper = mount(IncassoSetUp)
    await settle()
    expect(wrapper.get('[data-testid="incasso-current"]').text()).toContain("from the day your membership starts")
  })

  it("goes on the signup's token during a signup, and reads nothing without a session", async () => {
    const wrapper = mount(IncassoSetUp, {props: {signupToken: "tok"}})
    await settle()
    expect(api.findOwnMandate).not.toHaveBeenCalled()

    await fill(wrapper)
    await wrapper.get('[data-testid="incasso-form"]').trigger("submit")
    await settle()

    expect(api.setUpMandate).toHaveBeenCalledWith({
      headers: {[SIGNUP_TOKEN_HEADER]: "tok"},
      body: {iban: "NL91 ABNA 0417 1643 00", accountHolder: "Ann Vos", authorised: true},
    })
    expect(api.setUpOwnMandate).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="incasso-saved"]').exists()).toBe(true)
  })

  it("says why the details were refused, and cancels", async () => {
    api.setUpOwnMandate.mockResolvedValue({status: 400, error: {code: "InvalidIban", detail: "That is not a valid IBAN."}})
    api.setUpMandate.mockResolvedValue({status: 400, error: {code: "InvalidIban", detail: "That is not a valid IBAN."}})
    const wrapper = mount(IncassoSetUp)
    await settle()
    await fill(wrapper)
    await wrapper.get('[data-testid="incasso-form"]').trigger("submit")
    await settle()
    expect(wrapper.get('[data-testid="incasso-failure"]').text()).toContain("not a valid IBAN")

    await wrapper.get('[data-testid="incasso-cancel"]').trigger("click")
    expect(wrapper.find('[data-testid="incasso-form"]').exists()).toBe(false)

    const signup = mount(IncassoSetUp, {props: {signupToken: "tok"}})
    await fill(signup)
    await signup.get('[data-testid="incasso-form"]').trigger("submit")
    await settle()
    expect(signup.get('[data-testid="incasso-failure"]').text()).toContain("not a valid IBAN")
  })
})
