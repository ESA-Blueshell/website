package net.blueshell.api.cohort.domain

import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.refusal.Refusal
import org.springframework.http.HttpStatus

// A code and the facts, never the sentence: `cohorts/refusals.ts` writes that. See ADR-026.
sealed class TargetRefusal(
    status: HttpStatus,
    code: String,
    summary: String,
    facts: Map<String, Any>,
) : Refusal(status, code, summary, facts)

class TargetSystemRefused(
    system: TargetSystem,
    reason: String,
) : TargetRefusal(
        HttpStatus.BAD_GATEWAY,
        "TargetSystemRefused",
        "The system refused the change.",
        mapOf("system" to system.shownName, "reason" to reason),
    )

class TargetNotFound(
    system: TargetSystem,
    externalId: String,
) : TargetRefusal(
        HttpStatus.NOT_FOUND,
        "TargetNotFound",
        "The system has no such target.",
        mapOf("system" to system.shownName, "externalId" to externalId),
    )

class TargetStillLinked(
    system: TargetSystem,
    externalId: String,
) : TargetRefusal(
        HttpStatus.CONFLICT,
        "TargetStillLinked",
        "A list linked to a cohort cannot be deleted.",
        mapOf("system" to system.shownName, "externalId" to externalId),
    )

class TargetNameMismatch(
    name: String,
) : TargetRefusal(
        HttpStatus.BAD_REQUEST,
        "TargetNameMismatch",
        "The name typed is not the list's name.",
        mapOf("name" to name),
    )

class TargetNotOfCohort(
    targetId: Long,
) : TargetRefusal(
        HttpStatus.NOT_FOUND,
        "TargetNotOfCohort",
        "The cohort has no such target.",
        mapOf("cohortId" to targetId),
    )

class TargetNotCreated(
    targetId: Long,
) : TargetRefusal(
        HttpStatus.CONFLICT,
        "TargetNotCreated",
        "The target has not been created yet.",
        mapOf("cohortId" to targetId),
    )
