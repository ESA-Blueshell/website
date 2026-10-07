package net.blueshell.api.email.domain

import net.blueshell.api.shared.refusal.Refusal
import org.springframework.http.HttpStatus

// A code and the facts, never the sentence: `domains/emails/refusals.ts` writes that. See ADR-026.
sealed class SendingAddressRefusal(
    status: HttpStatus,
    code: String,
    summary: String,
    facts: Map<String, Any> = emptyMap(),
) : Refusal(status, code, summary, facts)

class SendingAddressNotFound : SendingAddressRefusal(HttpStatus.NOT_FOUND, "SendingAddressNotFound", "There is no such sending address.")

class SendingAddressTaken(
    address: String,
) : SendingAddressRefusal(
        HttpStatus.CONFLICT,
        "SendingAddressTaken",
        "That address is a sending address already.",
        mapOf("address" to address),
    )

class SendingAddressNeedsLogin :
    SendingAddressRefusal(
        HttpStatus.BAD_REQUEST,
        "SendingAddressNeedsLogin",
        "A new sending address, or one moved to another server, needs its SMTP username and password.",
    )

class ImapServerIncomplete :
    SendingAddressRefusal(HttpStatus.BAD_REQUEST, "ImapServerIncomplete", "An IMAP server needs its host, port and security.")

class MailLoginRefused(
    protocol: MailProtocol,
    reason: String,
) : SendingAddressRefusal(
        HttpStatus.BAD_REQUEST,
        "MailLoginRefused",
        "The mail server refused the login.",
        mapOf("protocol" to protocol.name, "reason" to reason),
    )

class MailNeedsEncryption(
    protocol: MailProtocol,
    host: String,
) : SendingAddressRefusal(
        HttpStatus.BAD_REQUEST,
        "MailNeedsEncryption",
        "A login only goes unencrypted to a server on the site's own network.",
        mapOf("protocol" to protocol.name, "host" to host),
    )

class SendingLoginsUnavailable :
    SendingAddressRefusal(
        HttpStatus.SERVICE_UNAVAILABLE,
        "SendingLoginsUnavailable",
        "The store the logins are kept in cannot be reached now.",
    )
