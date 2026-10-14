import {beforeEach, describe, expect, it, vi} from "vitest"
import PaymentDetails from "@/pages/login/PaymentDetails.vue"
import {BulkFeeType, ContributionEmailKind, type OwnContributions} from "@/services/api"
import {mountInApp, settle} from "../helpers"

const api = vi.hoisted(() => ({findOwnContributions: vi.fn(), findBankAccount: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

vi.mock("@/components/common/AccountFrame.vue", () => ({
  default: {name: "AccountFrame", props: {heading: String, islandContent: Boolean}, template: "<div><slot /></div>"},
}))

const BANK = {iban: "NL00 TEST 0000 0000 00", bic: "TESTNL2A", accountName: "Test Vereniging"}

const signedInWith = async (own: OwnContributions | null, bank: typeof BANK | null = BANK) => {
  api.findOwnContributions.mockResolvedValue(own ? {status: 200, data: own} : {status: 500, error: {}})
  api.findBankAccount.mockResolvedValue(bank ? {status: 200, data: bank} : {status: 500, error: {}})
  const page = mountInApp(PaymentDetails)
  await settle()
  return page
}

const THIS_YEAR = {
  periodId: 3, startDate: "2026-09-01", endDate: "2027-08-31", feeType: BulkFeeType.FULL_YEAR_FEE, fee: 30,
  lastEmailKind: ContributionEmailKind.REMINDER, lastEmailAt: "2026-09-12T10:00:00Z", dueDate: "2026-09-26",
}
const LAST_YEAR = {
  periodId: 2, startDate: "2025-09-01", endDate: "2026-08-31", feeType: BulkFeeType.HALF_YEAR_FEE, fee: 15, paidAt: "2026-03-04T10:00:00Z",
}

describe("the Payment details page", () => {
  beforeEach(() => vi.clearAllMocks())

  it("is an account page of its own, drawn in the island", async () => {
    const frame = (await signedInWith({held: true, paysByIncasso: false, periods: []})).getComponent({name: "AccountFrame"})

    expect(frame.props("heading")).toBe("Payment details")
    expect(frame.props("islandContent")).toBe(true)
  })

  it("lists each period newest first, says what is unpaid and when it was last paid", async () => {
    const page = await signedInWith({held: true, paysByIncasso: false, periods: [THIS_YEAR, LAST_YEAR]})

    expect(page.get("[data-testid=payment-details-period-3]").text()).toContain("2026-2027")
    expect(page.get("[data-testid=payment-details-period-3]").text()).toContain("Last asked 12 September 2026 · Pay by 26 September 2026")
    expect(page.get("[data-testid=payment-details-period-3]").text()).toContain("Not paid yet")
    expect(page.get("[data-testid=payment-details-period-2]").text()).toContain("Paid 4 March 2026")
    expect(page.get("[data-testid=payment-details-owed]").text()).toContain("€ 30.00")
    expect(page.get("[data-testid=payment-details-owed]").text()).toContain("1 unpaid contribution")
    expect(page.get("[data-testid=payment-details-last-paid]").text()).toContain("4 March 2026")
    expect(page.get("[data-testid=payment-details-method]").text()).toContain("Transfer or cash")
  })

  it("counts several unpaid contributions, and nothing where all are paid", async () => {
    const several = await signedInWith({held: true, paysByIncasso: false, periods: [THIS_YEAR, {...THIS_YEAR, periodId: 2}]})
    expect(several.get("[data-testid=payment-details-owed]").text()).toContain("2 unpaid contributions")

    const paid = await signedInWith({held: true, paysByIncasso: false, periods: [LAST_YEAR]})
    expect(paid.get("[data-testid=payment-details-owed]").text()).toContain("Nothing")
  })

  it("lists every way to pay, and how to switch to direct debit", async () => {
    const page = await signedInWith({held: true, paysByIncasso: false, periods: []})

    expect(page.get("[data-testid=payment-details-bank]").text()).toContain("Transfer to NL00 TEST 0000 0000 00, in the name of Test Vereniging")
    expect(page.get("[data-testid=payment-details-switch]").text()).toContain("mandate form")
    expect(page.find("[data-testid=payment-details-incasso]").exists()).toBe(false)
    expect(page.find("[data-testid=payment-details-no-periods]").exists()).toBe(true)
    expect(page.getComponent({name: "CutButton"}).props("href")).toBe("mailto:board@blueshell.utwente.nl")
  })

  it("says a member on direct debit pays that way, and still lists the others", async () => {
    const page = await signedInWith({held: true, paysByIncasso: true, periods: []}, null)

    expect(page.get("[data-testid=payment-details-incasso]").text()).toBe("You pay by direct debit. To stop it, email the board.")
    expect(page.get("[data-testid=payment-details-method]").text()).toContain("Direct debit")
    expect(page.get("[data-testid=payment-details-bank]").text()).toContain("The payment email names the account")
    expect(page.find("[data-testid=payment-details-switch]").exists()).toBe(false)
  })

  it("says so to somebody who never joined, and where the page could not be read", async () => {
    const never = await signedInWith({held: false, paysByIncasso: false, periods: []})
    expect(never.get("[data-testid=payment-details-none]").text()).toContain("You are not a member")

    const unread = await signedInWith(null)
    expect(unread.find("[data-testid=payment-details-unread]").exists()).toBe(true)
  })
})
