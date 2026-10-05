import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import ManualPayment from "@/components/account/ManualPayment.vue"

const api = vi.hoisted(() => ({findBankAccount: vi.fn()}))
vi.mock("@/services/api", async (importOriginal) => ({...(await importOriginal<typeof import("@/services/api")>()), ...api}))

describe("paying a contribution by hand", () => {
  beforeEach(() => vi.clearAllMocks())

  it("names the association's account for a transfer, and where cash goes", async () => {
    api.findBankAccount.mockResolvedValue({status: 200, data: {iban: "NL00 TEST 0000 0000 00", bic: "TESTNL2A", accountName: "Test Vereniging"}})
    const wrapper = mount(ManualPayment)
    await flushPromises()

    const bank = wrapper.get('[data-testid="manual-payment-bank"]').text()
    expect(bank).toContain("NL00 TEST 0000 0000 00, in the name of Test Vereniging")
    expect(bank).toContain("the BIC code is TESTNL2A")
    expect(wrapper.text()).toContain("postbus 49 in the Bastille")
    expect(wrapper.text()).toContain("The treasurer emails you a payment request")
  })

  it("points at the payment request where the account cannot be read", async () => {
    api.findBankAccount.mockResolvedValue({status: 500, error: {}})
    const wrapper = mount(ManualPayment)
    await flushPromises()

    expect(wrapper.find('[data-testid="manual-payment-bank"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="manual-payment-bank-unread"]').text()).toContain("The payment request names the account")
  })
})
