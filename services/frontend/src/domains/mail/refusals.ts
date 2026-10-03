// TWIN: `mail/domain/MailRefusal.kt` declares the codes and their facts. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

const sentences: Record<string, (r: RefusalCode) => string> = {
  NobodyToWrite: () => "Nobody it is addressed to has an email address. Add a group or a person.",
  SubjectMissing: () => "Give the email a subject.",
  MessageMissing: () => "Write a message.",
  InboxMessageNotFound: () => "That message is no longer in the inbox.",
}

export const {refusable} = refusalReader(sentences)
