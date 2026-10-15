// TWIN: `user/domain/MandateRefusal.kt` and `sealing/Sealer.kt` declare the codes. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

const sentences: Record<string, (r: RefusalCode) => string> = {
  InvalidIban: () => "That is not a valid IBAN. Check it against your bank statement.",
  AccountHolderMissing: () => "Add the name on the account.",
  BicMissing: () => "Your bank is outside the Netherlands. Add its BIC.",
  InvalidBic: () => "That is not a valid BIC. Check it with your bank.",
  SealingUnavailable: () => "Your bank account cannot be saved right now, and nothing was changed. Try again in a moment.",
}

export const {reasonFor} = refusalReader(sentences)
