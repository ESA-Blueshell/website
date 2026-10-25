/**
 * A person's own declarations and their receipts (api ADR-041). Receipts are private files: their
 * urls open only for the submitter and the board, through the sign-in cookie.
 */
import {maskedIban} from "@/domains/banking"
import {apiUrl} from "@/services/api/apiUrl"
import {
  approveDeclaration as approve,
  findDeclaration,
  findDeclarationBankAccount,
  markDeclarationPaid as markPaid,
  listDeclarations as listAll,
  rejectDeclaration as reject,
  sendBackDeclaration as sendBack,
  type DeclarationDraftRequest,
  type DeclarationResponse,
  deleteDeclarationDraft,
  downloadDeclarationsPdf,
  findOwnDeclaration,
  listOwnDeclarations as listOwn,
  type ReceiptResponse,
  saveDeclarationDraft,
  startDeclaration,
  submitDeclaration as submit,
  uploadReceipt as upload,
  withdrawDeclaration as withdraw,
} from "@/services/api"
import type {Refused} from "@/types/api"
import {readBlobRefusal, readOr} from "@/utils/answers"
import type {Saved} from "@/utils/refusals"
import {centsOf, type Declaration, type DeclarationDraft, type Expense, type BankAccountToPay, type Receipt} from "../declarations"
import {accepted, refusable} from "../refusals"

const asReceipt = (receipt: ReceiptResponse): Receipt => ({
  id: receipt.id,
  path: `${receipt.id}.${receipt.mediaType === "application/pdf" ? "pdf" : "webp"}`,
  name: receipt.name,
  type: receipt.mediaType,
  url: apiUrl(receipt.url),
})

const shown = (declaration: DeclarationResponse): Declaration => ({
  id: declaration.id,
  submitterId: declaration.submitterId,
  submitter: declaration.submitter,
  state: declaration.state,
  confirmed: declaration.confirmed,
  createdAt: declaration.createdAt,
  receipts: declaration.receipts.map(asReceipt),
  expenses: declaration.expenses.map((expense): Expense => ({
    id: String(expense.id),
    paidOn: expense.paidOn ?? "",
    description: expense.description,
    amount: expense.amountCents == null ? "" : (expense.amountCents / 100).toFixed(2),
    for: expense.bookedTo ?? null,
    receipts: expense.receipts.map(asReceipt),
  })),
  steps: declaration.steps.map(step => ({
    state: step.state,
    at: step.at,
    by: step.by ?? "",
    ...(step.reason ? {reason: step.reason} : {}),
    ...(step.paidToCountry && step.paidToLastTwo ? {paidTo: maskedIban(step.paidToCountry, step.paidToLastTwo)} : {}),
  })),
})

const written = (draft: DeclarationDraft): DeclarationDraftRequest => ({
  confirmed: draft.confirmed,
  receipts: draft.receipts.map(receipt => receipt.id),
  expenses: draft.expenses.map(expense => ({
    paidOn: expense.paidOn || undefined,
    description: expense.description,
    amountCents: centsOf(expense.amount) ?? undefined,
    bookedTo: expense.for ? {kind: expense.for.kind, id: expense.for.id} : undefined,
    receipts: expense.receipts.map(receipt => receipt.id),
  })),
})

/** A write answering with a declaration, as the page reads one; the name ends as the write sweep looks for. */
const declarationRefusable = async (call: ReturnType<typeof submit>, fallback: string): Promise<Saved<Declaration> | Refused> => {
  const res = await refusable(call, fallback)
  return res.ok ? {ok: true, saved: shown(res.saved)} : res
}

/** The reader's declarations, newest first, or nothing where they could not be read. */
export async function listOwnDeclarations(): Promise<Declaration[] | null> {
  const held = await readOr(listOwn(), null)
  return held ? held.map(shown) : null
}

export async function readOwnDeclaration(id: number): Promise<Declaration | null> {
  const held = await readOr(findOwnDeclaration({path: {id}}), null)
  return held ? shown(held) : null
}

/** Starts a declaration where [id] is nothing, or saves the draft over the one it names. */
export function saveDraft(id: number | null, draft: DeclarationDraft): Promise<Saved<Declaration> | Refused> {
  const body = written(draft)
  const fallback = "We could not save the declaration. Try again."
  return id == null ? declarationRefusable(startDeclaration({body}), fallback) : declarationRefusable(saveDeclarationDraft({path: {id}, body}), fallback)
}

export const submitDeclaration = (id: number) =>
  declarationRefusable(submit({path: {id}}), "We could not submit the declaration. Try again.")

export const withdrawDeclaration = (id: number) =>
  declarationRefusable(withdraw({path: {id}}), "We could not withdraw the declaration. Try again.")

/** Every declaration that reached the board, newest first, or nothing where they could not be read. */
export async function listDeclarations(): Promise<Declaration[] | null> {
  const held = await readOr(listAll(), null)
  return held ? held.map(shown) : null
}

/** One declaration as the board reads it, or nothing where there is none or it could not be read. */
export async function readDeclaration(id: number): Promise<Declaration | null> {
  const held = await readOr(findDeclaration({path: {id}}), null)
  return held ? shown(held) : null
}

export const approveDeclaration = (id: number) =>
  declarationRefusable(approve({path: {id}}), "We could not approve the declaration. Try again.")

/** Back to a draft for its submitter to change; they are emailed [reason]. */
export const sendBackDeclaration = (id: number, reason: string) =>
  declarationRefusable(sendBack({path: {id}, body: {reason}}), "We could not send the declaration back. Try again.")

/** Final; the submitter is emailed [reason]. */
export const rejectDeclaration = (id: number, reason: string) =>
  declarationRefusable(reject({path: {id}, body: {reason}}), "We could not reject the declaration. Try again.")

/** The board transferred the money by hand; it went to the account on file now, and the submitter is emailed. */
export const markDeclarationPaid = (id: number) =>
  declarationRefusable(markPaid({path: {id}}), "We could not mark the declaration as paid. Try again.")

/**
 * The submitter's account on file now, as the board transfers to it, or nothing where none is on
 * file; undefined where it could not be read. The full IBAN opens only through a logged reveal.
 */
export async function readBankAccountToPay(id: number): Promise<BankAccountToPay | null | undefined> {
  const held = await readOr(findDeclarationBankAccount({path: {id}}), undefined)
  if (!held) return undefined
  const account = held.bankAccount
  return account ? {holder: account.accountHolder ?? null, preview: maskedIban(account.ibanCountry, account.ibanLastTwo), bic: account.bic ?? null} : null
}

/** Declarations as one PDF in the layout of the paper form, each followed by its receipts; the api builds it as it answers. */
export async function fetchDeclarationsPdf(ids: number[]): Promise<{ok: true; file: Blob} | Refused> {
  const answered = await refusable(readBlobRefusal(downloadDeclarationsPdf({query: {ids}})), "We could not make the PDF. Try again.")
  return answered.ok ? {ok: true, file: answered.saved as Blob} : answered
}

export const deleteDraft = (id: number) =>
  accepted(deleteDeclarationDraft({path: {id}}), "We could not delete the draft. Try again.")

/** The part of a photo to keep, as fractions of its width and height from the top left. */
export interface Crop {
  x: number
  y: number
  width: number
  height: number
}

const WHOLE: Crop = {x: 0, y: 0, width: 1, height: 1}
// TWIN: `FileType.RECEIPT` holds an upload to the same side and quality.
const LARGEST_SIDE = 1600

/*
 * A photo straight off a phone is several megabytes. It is cut to the bill and shrunk to what a
 * receipt needs to be read before it is sent; the api holds it to the same size whatever arrives.
 * A PDF is sent as it is.
 */
export async function uploadReceipt(file: File, crop: Crop = WHOLE): Promise<Saved<Receipt> | Refused> {
  const sent = file.type.startsWith("image/") ? await cropped(file, crop) : file
  const res = await refusable(upload({body: {file: sent}}), "We could not read that file. Try a photo or a PDF.")
  return res.ok ? {ok: true, saved: asReceipt(res.saved)} : res
}

async function cropped(file: File, crop: Crop): Promise<File> {
  const bitmap = await createImageBitmap(file)
  const cut = {
    x: Math.round(crop.x * bitmap.width),
    y: Math.round(crop.y * bitmap.height),
    width: Math.max(1, Math.round(crop.width * bitmap.width)),
    height: Math.max(1, Math.round(crop.height * bitmap.height)),
  }
  const scale = Math.min(1, LARGEST_SIDE / Math.max(cut.width, cut.height))
  const canvas = document.createElement("canvas")
  canvas.width = Math.round(cut.width * scale)
  canvas.height = Math.round(cut.height * scale)
  canvas.getContext("2d")!.drawImage(bitmap, cut.x, cut.y, cut.width, cut.height, 0, 0, canvas.width, canvas.height)
  const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, "image/webp", 0.82))
  if (!blob) throw new Error("The photo could not be converted.")
  return new File([blob], file.name.replace(/\.[^.]+$/, ".webp"), {type: blob.type})
}
