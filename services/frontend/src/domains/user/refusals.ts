// TWIN: `user/domain/RoleRefusal.kt` declares the codes and their facts. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

interface RefusalBody extends RefusalCode {
  role?: string
}

const sentences: Record<string, (r: RefusalBody) => string> = {
  RoleNotAssignable: r =>
    `${r.role ?? "That role"} is not a role an admin hands out. `
    + "Member follows a membership and committee follows a committee seat.",
  LastAdministrator: () =>
    "This is the last administrator. Give somebody else admin first, "
    + "so that nobody is locked out of the site.",
}

export const {sentenceFor, reasonFor} = refusalReader(sentences)
