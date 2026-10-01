package net.blueshell.api.mail.domain

import net.blueshell.api.shared.refusal.Refusal
import org.springframework.http.HttpStatus

// A code and the facts, never the sentence: `domains/mail/refusals.ts` writes that. See ADR-026.
sealed class MailRefusal(
    status: HttpStatus,
    code: String,
    summary: String,
    facts: Map<String, Any> = emptyMap(),
) : Refusal(status, code, summary, facts)

class NobodyToWrite : MailRefusal(HttpStatus.BAD_REQUEST, "NobodyToWrite", "Nobody it is addressed to has an email address.")

class SubjectMissing : MailRefusal(HttpStatus.BAD_REQUEST, "SubjectMissing", "An email has a subject.")

class MessageMissing : MailRefusal(HttpStatus.BAD_REQUEST, "MessageMissing", "An email says something.")
