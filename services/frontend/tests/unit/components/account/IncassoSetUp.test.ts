import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import IncassoSetUp from "@/components/account/IncassoSetUp.vue"
import {SIGNUP_TOKEN_HEADER} from "@/plugins/signupContinuation"
import {settle} from "../../helpers/testUtils"

const api = vi.hoisted(() => ({setUpMandate: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const fill = async (wrapper: ReturnType<typeof mount>) => {
  await wrapper.get('[data-testid="incasso-open"]').trigger("click")
  const fields = wrapper.findAllComponents({name: "FormControl"})
  await fields[0]!.vm.$emit("update:modelValue", "NL91 ABNA 0417 1643 00")
  await fields[1]!.vm.$emit("update:modelValue", "Ann Vos")
  await wrapper.findComponent({name: "CheckBox"}).vm.$emit("update:modelValue", true)
}

describe("the signup's incasso step", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.setUpMandate.mockResolvedValue({status: 204, data: undefined})
  })

  it("goes on the signup's token, and asks no address since the signup took one", async () => {
    const wrapper = mount(IncassoSetUp, {props: {signupToken: "tok"}})
    await fill(wrapper)
    expect(wrapper.find('[data-testid="incasso-street"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="incasso-address-note"]').text()).toContain("address you gave in this signup")
    await wrapper.get('[data-testid="incasso-form"]').trigger("submit")
    await settle()

    expect(api.setUpMandate).toHaveBeenCalledWith({
      headers: {[SIGNUP_TOKEN_HEADER]: "tok"},
      body: {iban: "NL91 ABNA 0417 1643 00", accountHolder: "Ann Vos", authorised: true, wordingVersion: "2026-10"},
    })
    expect(wrapper.find('[data-testid="incasso-saved"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="incasso-form"]').exists()).toBe(false)
  })

  it("says why the details were refused, and cancels", async () => {
    api.setUpMandate.mockResolvedValue({status: 400, error: {code: "InvalidIban", detail: "That is not a valid IBAN."}})
    const wrapper = mount(IncassoSetUp, {props: {signupToken: "tok"}})
    await fill(wrapper)
    await wrapper.get('[data-testid="incasso-form"]').trigger("submit")
    await settle()
    expect(wrapper.get('[data-testid="incasso-failure"]').text()).toContain("not a valid IBAN")

    await wrapper.get('[data-testid="incasso-cancel"]').trigger("click")
    expect(wrapper.find('[data-testid="incasso-form"]').exists()).toBe(false)
  })
})
