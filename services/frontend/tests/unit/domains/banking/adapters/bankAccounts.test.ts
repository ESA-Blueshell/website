import {beforeEach, describe, expect, it, vi} from "vitest"
import {readOwnBankAccount, removeOwnBankAccount, saveOwnBankAccount} from "@/domains/banking/adapters/bankAccounts"
import {deleteOwnBankAccount, findOwnBankAccount, replaceOwnBankAccount} from "@/services/api"
import {answer, emptyAnswer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  deleteOwnBankAccount: vi.fn(),
  findOwnBankAccount: vi.fn(),
  replaceOwnBankAccount: vi.fn(),
}))

const held = {ibanCountry: "NL", ibanLastTwo: "00", bic: null, recordedAt: "2026-10-15T10:00:00Z"}

describe("the reader's bank account", () => {
  beforeEach(() => vi.clearAllMocks())

  it("is read as its preview, and as nothing where none is on file", async () => {
    vi.mocked(findOwnBankAccount).mockResolvedValueOnce(answer(findOwnBankAccount, held))
    await expect(readOwnBankAccount()).resolves.toEqual({preview: "NL •• … ••00", addedAt: "2026-10-15T10:00:00Z"})

    vi.mocked(findOwnBankAccount).mockResolvedValueOnce(emptyAnswer(findOwnBankAccount))
    await expect(readOwnBankAccount()).resolves.toBeNull()
  })

  it("is saved whole, a Dutch one without a BIC, and says why it was refused or that a step-up is asked", async () => {
    vi.mocked(replaceOwnBankAccount).mockResolvedValueOnce(answer(replaceOwnBankAccount, held))
    await expect(saveOwnBankAccount({holder: "Ann Vos", iban: "NL91ABNA0417164300", bic: ""}))
      .resolves.toEqual({ok: true, saved: {preview: "NL •• … ••00", addedAt: "2026-10-15T10:00:00Z"}})
    expect(replaceOwnBankAccount).toHaveBeenCalledWith({body: {iban: "NL91ABNA0417164300", accountHolder: "Ann Vos", bic: undefined}})

    vi.mocked(replaceOwnBankAccount).mockResolvedValueOnce(refusal(replaceOwnBankAccount, {code: "BicMissing"}))
    await expect(saveOwnBankAccount({holder: "Ann", iban: "DE89370400440532013000", bic: ""}))
      .resolves.toEqual({ok: false, reason: "Your bank is outside the Netherlands. Add its BIC.", needsStepUp: false})

    for (const [code, said] of [["InvalidIban", "Check it against your bank statement."], ["AccountHolderMissing", "Add the name"], ["SealingUnavailable", "cannot be saved right now"]]) {
      vi.mocked(replaceOwnBankAccount).mockResolvedValueOnce(refusal(replaceOwnBankAccount, {code}))
      await expect(saveOwnBankAccount({holder: "Ann", iban: "NL91ABNA0417164300", bic: ""})).resolves.toMatchObject({reason: expect.stringContaining(said)})
    }

    vi.mocked(replaceOwnBankAccount).mockResolvedValueOnce(refusal(replaceOwnBankAccount, {code: "StepUpRequired"}, 403))
    await expect(saveOwnBankAccount({holder: "Ann", iban: "NL91ABNA0417164300", bic: ""})).resolves.toMatchObject({needsStepUp: true})
  })

  it("is deleted, or says why not", async () => {
    vi.mocked(deleteOwnBankAccount).mockResolvedValueOnce(emptyAnswer(deleteOwnBankAccount))
    await expect(removeOwnBankAccount()).resolves.toEqual({ok: true})

    vi.mocked(deleteOwnBankAccount).mockResolvedValueOnce(refusal(deleteOwnBankAccount, {code: "StepUpRequired"}, 403))
    await expect(removeOwnBankAccount()).resolves.toMatchObject({ok: false, needsStepUp: true})
  })
})
