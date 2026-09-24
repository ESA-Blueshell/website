package net.blueshell.api.auth.domain

import org.springframework.http.HttpStatus

// A code and the facts, never the sentence: the frontend writes that. See api ADR-026.
sealed class AccountSecurityRefusal(
    val status: HttpStatus,
    val code: String,
    val summary: String,
    val facts: Map<String, Any> = emptyMap(),
) : RuntimeException(summary)

class WrongPassword : AccountSecurityRefusal(HttpStatus.FORBIDDEN, "WrongPassword", "That password is not right.")

class WrongCode(
    triesLeft: Int?,
) : AccountSecurityRefusal(
        HttpStatus.UNAUTHORIZED,
        "WrongCode",
        "That code is not right.",
        triesLeft?.let { mapOf("triesLeft" to it) } ?: emptyMap(),
    )

class ChallengeExpired :
    AccountSecurityRefusal(HttpStatus.UNAUTHORIZED, "ChallengeExpired", "Sign in with your password again.")

class CodeLimitReached :
    AccountSecurityRefusal(HttpStatus.TOO_MANY_REQUESTS, "CodeLimitReached", "Too many wrong codes. Try again later.")

class TwoFactorAlreadyOn :
    AccountSecurityRefusal(HttpStatus.CONFLICT, "TwoFactorAlreadyOn", "Two-factor is already on.")

class NothingToConfirm :
    AccountSecurityRefusal(HttpStatus.CONFLICT, "NothingToConfirm", "Start setting up two-factor first.")

class TwoFactorRequired :
    AccountSecurityRefusal(HttpStatus.CONFLICT, "TwoFactorRequired", "Holding a granted role requires two-factor.")

class TwoFactorOff : AccountSecurityRefusal(HttpStatus.CONFLICT, "TwoFactorOff", "Two-factor is not on.")

class NotLocked : AccountSecurityRefusal(HttpStatus.CONFLICT, "NotLocked", "That account is not locked.")

class NotAwaitingReenrolment :
    AccountSecurityRefusal(HttpStatus.CONFLICT, "NotAwaitingReenrolment", "That person is not waiting to set up again.")

class OwnAccount : AccountSecurityRefusal(HttpStatus.FORBIDDEN, "OwnAccount", "Another admin has to do this for you.")

class ReenrolmentRequired :
    AccountSecurityRefusal(HttpStatus.FORBIDDEN, "ReenrolmentRequired", "Use the re-enrolment link in your email.")

class AccountLocked :
    AccountSecurityRefusal(HttpStatus.FORBIDDEN, "AccountLocked", "This account is locked.")

class EmailTaken : AccountSecurityRefusal(HttpStatus.CONFLICT, "EmailTaken", "That address belongs to another account.")
