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

class SmtpLoginRefused(
    reason: String,
) : SendingAddressRefusal(HttpStatus.BAD_REQUEST, "SmtpLoginRefused", "The SMTP server refused the login.", mapOf("reason" to reason))

class SmtpNeedsEncryption(
    host: String,
) : SendingAddressRefusal(
        HttpStatus.BAD_REQUEST,
        "SmtpNeedsEncryption",
        "A login only goes unencrypted to a server on the site's own network.",
        mapOf("host" to host),
    )

class SendingLoginsUnavailable :
    SendingAddressRefusal(
        HttpStatus.SERVICE_UNAVAILABLE,
        "SendingLoginsUnavailable",
        "The store the logins are kept in cannot be reached now.",
    )
