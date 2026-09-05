// TWIN: `board/domain/BoardRefusal.kt` declares the codes and their facts. See ADR-026.

import {countOf} from "./copy"
import {refusalReader, type RefusalCode} from "@/utils/refusals"

export const boardHoldsMembers = (number: number, members: number) =>
  `Board ${number} still has ${countOf(members, "member", "members")} on it, so it cannot be removed. `
  + "Every one of them is a place somebody held in the association's history. Remove the "
  + "members first, and the board goes with them."

interface RefusalBody extends RefusalCode {
  number?: number
  members?: number
}

const sentences: Record<string, (r: RefusalBody) => string> = {
  BoardHoldsMembers: r => boardHoldsMembers(r.number ?? 0, r.members ?? 0),
}

export const {sentenceFor, reasonFor} = refusalReader(sentences)
