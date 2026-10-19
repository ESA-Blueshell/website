/**
 * A person's own declarations and their receipts (api ADR-041). Receipts are private files: their
 * urls open only for the submitter and the board, through the sign-in cookie.
 */
import {apiUrl} from "@/services/api/apiUrl"
import {
  type DeclarationDraftRequest,
  type DeclarationResponse,
  deleteDeclarationDraft,
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
import {readOr} from "@/utils/answers"
import type {Saved} from "@/utils/refusals"
import {centsOf, type Declaration, type DeclarationDraft, type Expense, type Receipt} from "../declarations"
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
  steps: declaration.steps.map(step => ({state: step.state, at: step.at, by: step.by ?? "", ...(step.reason ? {reason: step.reason} : {})})),
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
