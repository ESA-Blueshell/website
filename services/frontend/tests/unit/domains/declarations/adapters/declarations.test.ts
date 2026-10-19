import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {
  deleteDraft,
  listOwnDeclarations,
  readOwnDeclaration,
  saveDraft,
  submitDeclaration,
  uploadReceipt,
  withdrawDeclaration,
} from "@/domains/declarations/adapters/declarations"
import {
  BookingKind,
  deleteDeclarationDraft,
  DeclarationState,
  type DeclarationResponse,
  findOwnDeclaration,
  listOwnDeclarations as listOwn,
  saveDeclarationDraft,
  startDeclaration,
  submitDeclaration as submit,
  uploadReceipt as upload,
  withdrawDeclaration as withdraw,
} from "@/services/api"
import {answer, emptyAnswer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  deleteDeclarationDraft: vi.fn(),
  findOwnDeclaration: vi.fn(),
  listOwnDeclarations: vi.fn(),
  saveDeclarationDraft: vi.fn(),
  startDeclaration: vi.fn(),
  submitDeclaration: vi.fn(),
  uploadReceipt: vi.fn(),
  withdrawDeclaration: vi.fn(),
}))

const pdf = {id: 502, name: "shop.pdf", mediaType: "application/pdf", url: "/declarations/receipts/502"}
const photo = {id: 501, name: "bill.webp", mediaType: "image/webp", url: "/declarations/receipts/501"}

const held: DeclarationResponse = {
  id: 40,
  state: DeclarationState.DRAFT,
  submitterId: 12,
  submitter: "Ann Vos",
  confirmed: false,
  createdAt: "2026-10-01T10:00:00Z",
  receipts: [pdf],
  expenses: [
    {id: 41, paidOn: "2026-10-01", description: "Snacks", amountCents: 4835, bookedTo: {kind: BookingKind.EVENT, id: 7, name: "LAN"}, receipts: [photo]},
    {id: 42, paidOn: null, description: "", amountCents: null, bookedTo: {kind: BookingKind.COMMITTEE, id: 3, name: "Promo"}, receipts: []},
  ],
  steps: [
    {state: DeclarationState.DRAFT, at: "2026-10-01T10:00:00Z", by: "Ann Vos", reason: null},
    {state: DeclarationState.DRAFT, at: "2026-10-02T10:00:00Z", by: null, reason: "Missing receipt"},
  ],
}

describe("the reader's declarations", () => {
  beforeEach(() => vi.clearAllMocks())

  it("are read with amounts as the money field writes them, bookings in its words and receipts opened on the api", async () => {
    vi.mocked(listOwn).mockResolvedValueOnce(answer(listOwn, [held]))
    const [one] = (await listOwnDeclarations())!

    expect(one!.expenses[0]).toEqual({
      id: "41",
      paidOn: "2026-10-01",
      description: "Snacks",
      amount: "48.35",
      for: {kind: BookingKind.EVENT, id: 7, name: "LAN"},
      receipts: [{id: 501, path: "501.webp", name: "bill.webp", type: "image/webp", url: expect.stringMatching(/\/api\/declarations\/receipts\/501$/)}],
    })
    expect(one!.expenses[1]).toMatchObject({paidOn: "", amount: "", for: {kind: BookingKind.COMMITTEE}})
    expect(one!.receipts[0]!.path).toBe("502.pdf")
    expect(one!.steps).toEqual([
      {state: "DRAFT", at: "2026-10-01T10:00:00Z", by: "Ann Vos"},
      {state: "DRAFT", at: "2026-10-02T10:00:00Z", by: "", reason: "Missing receipt"},
    ])

    vi.mocked(listOwn).mockResolvedValueOnce(refusal(listOwn, null, 500))
    await expect(listOwnDeclarations()).resolves.toBeNull()
    vi.mocked(findOwnDeclaration).mockResolvedValueOnce(answer(findOwnDeclaration, held))
    await expect(readOwnDeclaration(40)).resolves.toMatchObject({id: 40})
    vi.mocked(findOwnDeclaration).mockResolvedValueOnce(refusal(findOwnDeclaration, {code: "DeclarationNotFound"}, 404))
    await expect(readOwnDeclaration(41)).resolves.toBeNull()
  })

  it("starts a draft or saves over one, sending cents, bookings and receipt ids", async () => {
    vi.mocked(startDeclaration).mockResolvedValueOnce(answer(startDeclaration, held))
    vi.mocked(saveDeclarationDraft).mockResolvedValueOnce(refusal(saveDeclarationDraft, {code: "UnknownBooking"}))
    const draft = {
      confirmed: true,
      receipts: [{id: 502, path: "502.pdf", name: "shop.pdf", type: "application/pdf", url: "u"}],
      expenses: [
        {id: "x", paidOn: "2026-10-01", description: "Snacks", amount: "48.35", for: {kind: BookingKind.EVENT, id: 7, name: "LAN"}, receipts: [{id: 501, path: "501.webp", name: "b", type: "image/webp", url: "u"}]},
        {id: "y", paidOn: "", description: "", amount: "", for: {kind: BookingKind.COMMITTEE, id: 3, name: "Promo"}, receipts: []},
        {id: "z", paidOn: "", description: "", amount: "", for: null, receipts: []},
      ],
    }

    await expect(saveDraft(null, draft)).resolves.toMatchObject({ok: true, saved: {id: 40}})
    expect(startDeclaration).toHaveBeenCalledWith({body: {
      confirmed: true,
      receipts: [502],
      expenses: [
        {paidOn: "2026-10-01", description: "Snacks", amountCents: 4835, bookedTo: {kind: BookingKind.EVENT, id: 7}, receipts: [501]},
        {paidOn: undefined, description: "", amountCents: undefined, bookedTo: {kind: BookingKind.COMMITTEE, id: 3}, receipts: []},
        {paidOn: undefined, description: "", amountCents: undefined, bookedTo: undefined, receipts: []},
      ],
    }})
    await expect(saveDraft(40, draft)).resolves.toEqual({ok: false, reason: "That committee or event no longer exists. Choose another one."})
    expect(saveDeclarationDraft).toHaveBeenCalledWith(expect.objectContaining({path: {id: 40}}))
  })

  it("submits, withdraws and deletes, each saying why the api refused", async () => {
    vi.mocked(submit).mockResolvedValueOnce(refusal(submit, {code: "ExpenseIncomplete", expense: 2, field: "amount"}))
    vi.mocked(submit).mockResolvedValueOnce(refusal(submit, {code: "ExpenseIncomplete", expense: 2}))
    vi.mocked(submit).mockResolvedValueOnce(refusal(submit, {code: "PaidInFuture", expense: 1}))
    vi.mocked(withdraw).mockResolvedValueOnce(answer(withdraw, held))
    vi.mocked(deleteDeclarationDraft).mockResolvedValueOnce(emptyAnswer(deleteDeclarationDraft))

    await expect(submitDeclaration(40)).resolves.toEqual({ok: false, reason: "Expense 2 needs an amount above zero."})
    await expect(submitDeclaration(40)).resolves.toEqual({ok: false, reason: "Expense 2 needs more details."})
    await expect(submitDeclaration(40)).resolves.toEqual({ok: false, reason: "The date you paid for expense 1 is in the future."})
    await expect(withdrawDeclaration(40)).resolves.toMatchObject({ok: true, saved: {id: 40}})
    await expect(deleteDraft(40)).resolves.toEqual({ok: true})
  })
})

describe("a receipt", () => {
  const drawn = vi.fn()
  let blob: Blob | null

  beforeEach(() => {
    vi.clearAllMocks()
    blob = new Blob(["webp"], {type: "image/webp"})
    vi.stubGlobal("createImageBitmap", vi.fn(async () => ({width: 4000, height: 2000})))
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({drawImage: drawn} as unknown as CanvasRenderingContext2D)
    vi.spyOn(HTMLCanvasElement.prototype, "toBlob").mockImplementation(callback => callback(blob))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it("is a photo cut to its crop and shrunk to 1600 pixels as WebP, or a PDF sent as it is", async () => {
    vi.mocked(upload).mockResolvedValue(answer(upload, photo))

    await expect(uploadReceipt(new File(["x"], "bill.jpg", {type: "image/jpeg"}), {x: 0.5, y: 0, width: 0.5, height: 1}))
      .resolves.toMatchObject({ok: true, saved: {id: 501, path: "501.webp"}})
    expect(drawn).toHaveBeenCalledWith(expect.anything(), 2000, 0, 2000, 2000, 0, 0, 1600, 1600)
    const sent = vi.mocked(upload).mock.calls[0]![0]!.body!.file as File
    expect(sent.name).toBe("bill.webp")
    expect(sent.type).toBe("image/webp")

    await uploadReceipt(new File(["x"], "whole.png", {type: "image/png"}))
    expect(drawn).toHaveBeenLastCalledWith(expect.anything(), 0, 0, 4000, 2000, 0, 0, 1600, 800)

    const shop = new File(["%PDF"], "shop.pdf", {type: "application/pdf"})
    await uploadReceipt(shop)
    expect(vi.mocked(upload).mock.calls.at(-1)![0]!.body!.file).toBe(shop)
  })

  it("is refused where the api refuses it, and where the photo cannot be converted", async () => {
    vi.mocked(upload).mockResolvedValueOnce(refusal(upload, null))
    await expect(uploadReceipt(new File(["%PDF"], "shop.pdf", {type: "application/pdf"})))
      .resolves.toEqual({ok: false, reason: "We could not read that file. Try a photo or a PDF."})

    blob = null
    await expect(uploadReceipt(new File(["x"], "bill.jpg", {type: "image/jpeg"}))).rejects.toThrow("could not be converted")
  })
})
