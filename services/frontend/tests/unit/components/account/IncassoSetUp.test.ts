import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import IncassoSetUp from "@/components/account/IncassoSetUp.vue"
import {IncassoStanding} from "@/services/api"
import {SIGNUP_TOKEN_HEADER} from "@/plugins/signupContinuation"
import {settle} from "../../helpers/testUtils"

const api = vi.hoisted(() => ({
  findOwnMandate: vi.fn(), setUpOwnMandate: vi.fn(), setUpMandate: vi.fn(), twoFactorStanding: vi.fn(), findAddressById: vi.fn(),
}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const own = {standing: IncassoStanding.MANDATE_RECORDED, ibanCountry: "NL", ibanLastTwo: "00", reference: "BLUESHELL-9-20260930", signedOn: "2026-09-30"}

const fill = async (wrapper: ReturnType<typeof mount>) => {
  await wrapper.get('[data-testid="incasso-open"]').trigger("click")
  const fields = wrapper.findAllComponents({name: "FormControl"})
  await fields[0]!.vm.$emit("update:modelValue", "NL91 ABNA 0417 1643 00")
  await fields[1]!.vm.$emit("update:modelValue", "Ann Vos")
  await wrapper.findComponent({name: "CheckBox"}).vm.$emit("update:modelValue", true)
}

describe("setting up incasso", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.findOwnMandate.mockResolvedValue({status: 200, data: {standing: IncassoStanding.NONE}})
    api.setUpOwnMandate.mockResolvedValue({status: 200, data: own})
    api.setUpMandate.mockResolvedValue({status: 204, data: undefined})
    api.twoFactorStanding.mockResolvedValue({status: 200, data: {on: false, required: false}})
  })

  it("asks the person to prove it is them when the api wants a step-up, and saves once they have", async () => {
    api.setUpOwnMandate.mockResolvedValueOnce({status: 403, error: {code: "StepUpRequired"}, response: {status: 403}})
    const wrapper = mount(IncassoSetUp, {global: {stubs: {StepUpDialog: {name: "StepUpDialog", props: ["modelValue", "twoFactorOn"], emits: ["proved", "update:modelValue"], template: "<div />"}}}})
    await settle()
    await fill(wrapper)
    await wrapper.get('[data-testid="incasso-form"]').trigger("submit")
    await settle()

    const dialog = wrapper.getComponent({name: "StepUpDialog"})
    expect(dialog.props("modelValue")).toBe(true)
    expect(dialog.props("twoFactorOn")).toBe(false)
    expect(wrapper.find('[data-testid="incasso-failure"]').exists()).toBe(false)

    dialog.vm.$emit("update:modelValue", false)
    await settle()
    expect(dialog.props("modelValue")).toBe(false)
    dialog.vm.$emit("proved")
    await settle()
    expect(api.setUpOwnMandate).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="incasso-saved"]').text()).toBe("Your bank details are saved.")
  })

  it("saves the member's bank details and shows only the account masked", async () => {
    const wrapper = mount(IncassoSetUp)
    await settle()
    expect(wrapper.find('[data-testid="incasso-none"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="incasso-open"]').text()).toBe("Set up incasso")

    await fill(wrapper)
    expect(wrapper.findComponent({name: "CheckBox"}).props("label")).toContain("I authorise ESA Blueshell")
    await wrapper.get('[data-testid="incasso-form"]').trigger("submit")
    await settle()

    expect(api.setUpOwnMandate).toHaveBeenCalledWith({body: {
      iban: "NL91 ABNA 0417 1643 00", accountHolder: "Ann Vos", authorised: true, wordingVersion: "2026-10",
      address: {country: "NL", city: "", street: "", houseNumber: "", zipCode: ""},
    }})
    expect(wrapper.get('[data-testid="incasso-current"]').text()).toContain("from the account NL•• … ••00")
    expect(wrapper.text()).not.toContain("0417")
    expect(wrapper.find('[data-testid="incasso-saved"]').exists()).toBe(true)
    expect(wrapper.emitted("saved")).toHaveLength(1)
    expect(wrapper.get('[data-testid="incasso-open"]').text()).toBe("Change bank details")
  })

  it("goes on the signup's token during a signup, and reads nothing without a session", async () => {
    const wrapper = mount(IncassoSetUp, {props: {signupToken: "tok"}})
    await settle()
    expect(api.findOwnMandate).not.toHaveBeenCalled()

    await fill(wrapper)
    // The signup's mandate takes the address the signup just took, so the step asks for none.
    expect(wrapper.find('[data-testid="incasso-street"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="incasso-address-note"]').text()).toContain("address you gave in this signup")
    await wrapper.get('[data-testid="incasso-form"]').trigger("submit")
    await settle()

    expect(api.setUpMandate).toHaveBeenCalledWith({
      headers: {[SIGNUP_TOKEN_HEADER]: "tok"},
      body: {iban: "NL91 ABNA 0417 1643 00", accountHolder: "Ann Vos", authorised: true, wordingVersion: "2026-10"},
    })
    expect(api.setUpOwnMandate).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="incasso-saved"]').exists()).toBe(true)
  })

  it("shows the address on the account to confirm or correct, and sends what is confirmed", async () => {
    api.findAddressById.mockResolvedValue({status: 200, data: {
      id: 5, opened: true, country: "NL", city: "Enschede", street: "Hallenweg", houseNumber: "5", zipCode: "7522NH", version: 0, createdAt: "", updatedAt: "",
    }})
    const wrapper = mount(IncassoSetUp, {props: {addressId: 5}})
    await settle()
    await fill(wrapper)
    expect(wrapper.findAllComponents({name: "FormControl"})[2]!.props("modelValue")).toBe("Hallenweg")

    const typed = wrapper.findAllComponents({name: "FormControl"})
    await typed[2]!.vm.$emit("update:modelValue", "Hallenweg")
    await typed[3]!.vm.$emit("update:modelValue", "7")
    await typed[4]!.vm.$emit("update:modelValue", "7522NH")
    await typed[5]!.vm.$emit("update:modelValue", "Enschede")
    await wrapper.findComponent({name: "CountrySelect"}).vm.$emit("update:modelValue", "DE")
    await wrapper.get('[data-testid="incasso-form"]').trigger("submit")
    await settle()

    expect(api.findAddressById).toHaveBeenCalledWith({path: {id: 5}, throwOnError: true})
    expect(api.setUpOwnMandate.mock.calls[0]![0].body.address)
      .toEqual({country: "DE", city: "Enschede", street: "Hallenweg", houseNumber: "7", zipCode: "7522NH"})
  })

  it("leaves the address to type in where the one on the account does not open", async () => {
    api.findAddressById.mockResolvedValue({status: 200, data: {id: 5, opened: false, version: 0, createdAt: "", updatedAt: ""}})
    const wrapper = mount(IncassoSetUp, {props: {addressId: 5}})
    await settle()
    await fill(wrapper)

    expect(wrapper.findAllComponents({name: "FormControl"})[2]!.props("modelValue")).toBe("")
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
