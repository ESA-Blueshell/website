// TWIN: `mail/domain/MailRefusal.kt` declares the codes and their facts. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

interface RefusalBody extends RefusalCode {
  replyTo?: string
  max?: number
}

const sentences: Record<string, (r: RefusalBody) => string> = {
  NobodyToWrite: () => "Nobody it is addressed to has an email address. Add a group or a person.",
  SubjectMissing: () => "Give the email a subject.",
  MessageMissing: () => "Write a message.",
  SubjectTooLong: (r) => `Make the subject shorter: at most ${r.max} characters.`,
  SubjectNotOneLine: () => "Write the subject on one line.",
  MessageTooLong: (r) => `Make the message shorter: at most ${r.max} characters.`,
  WriterWithoutAddress: () => "Your account has no email address, so a test has nowhere to go. Add one to your account first.",
  ReplyToNotAnAddress: (r) => `${r.replyTo} is not an email address. Type one address for replies, such as board@esa-blueshell.nl.`,
}

export const {refusable} = refusalReader(sentences)
