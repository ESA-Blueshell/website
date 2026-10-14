import {describe, expect, it, vi} from "vitest"
import {readBankAccount, readOwnContributions} from "@/domains/contribution/adapters/ownContributions"
import {findBankAccount, findOwnContributions} from "@/services/api"
import {answer, emptyAnswer} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findOwnContributions: vi.fn(),
  findBankAccount: vi.fn(),
}))

describe("the reader's own contributions", () => {
  it("answers with what the api read, or nothing where it read nothing", async () => {
    vi.mocked(findOwnContributions).mockResolvedValue(answer(findOwnContributions, {held: true, paysByIncasso: false, periods: []}))
    await expect(readOwnContributions()).resolves.toEqual({held: true, paysByIncasso: false, periods: []})

    vi.mocked(findOwnContributions).mockResolvedValue(emptyAnswer(findOwnContributions))
    await expect(readOwnContributions()).resolves.toBeNull()
  })

  it("answers with the account to transfer to", async () => {
    const bank = {iban: "NL00 TEST 0000 0000 00", bic: "TESTNL2A", accountName: "Test Vereniging"}
    vi.mocked(findBankAccount).mockResolvedValue(answer(findBankAccount, bank))

    await expect(readBankAccount()).resolves.toEqual(bank)
  })
})
