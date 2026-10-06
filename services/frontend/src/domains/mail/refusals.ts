// TWIN: `mail/domain/MailRefusal.kt` declares the codes and their facts. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

interface RefusalBody extends RefusalCode {
  replyTo?: string
}

const sentences: Record<string, (r: RefusalBody) => string> = {
  NobodyToWrite: () => "Nobody it is addressed to has an email address. Add a group or a person.",
  SubjectMissing: () => "Give the email a subject.",
  MessageMissing: () => "Write a message.",
  InboxMessageNotFound: () => "That message is no longer in the inbox.",
  ReplyToNotAnAddress: (r) => `${r.replyTo} is not an email address. Type one address for replies, such as board@esa-blueshell.nl.`,
}

export const {refusable} = refusalReader(sentences)
