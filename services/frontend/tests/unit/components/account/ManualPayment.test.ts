import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import ManualPayment from "@/components/account/ManualPayment.vue"

const api = vi.hoisted(() => ({findBankAccount: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

describe("paying by hand", () => {
  it("names the association's account, and leaves it to the payment request where it cannot be read", async () => {
    api.findBankAccount.mockResolvedValue({status: 200, data: {iban: "NL19 INGB 0008 0964 62", bic: "INGBNL2A", accountName: "Blueshell"}})
    const read = mount(ManualPayment)
    await flushPromises()
    expect(read.get('[data-testid="manual-payment-bank"]').text()).toContain("NL19 INGB 0008 0964 62, in the name of Blueshell")
    expect(read.text()).toContain("INGBNL2A")

    api.findBankAccount.mockResolvedValue({status: 500, error: {}})
    const unread = mount(ManualPayment)
    await flushPromises()
    expect(unread.find('[data-testid="manual-payment-bank"]').exists()).toBe(false)
    expect(unread.get('[data-testid="manual-payment-bank-unread"]').text()).toContain("The payment request names the account")
  })
})
