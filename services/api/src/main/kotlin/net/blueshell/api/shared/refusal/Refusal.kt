package net.blueshell.api.shared.refusal

import org.springframework.http.HttpStatus

/**
 * A write refused for a reason the reader can act on: a status, a code and the facts, never the
 * sentence, which the frontend writes. See ADR-026.
 *
 * Each module keeps its own sealed family under this, so which refusals a module can raise stays
 * the module's; `RefusalAdvice` answers all of them the same way.
 */
abstract class Refusal(
    val status: HttpStatus,
    val code: String,
    val summary: String,
    val facts: Map<String, Any> = emptyMap(),
) : RuntimeException(summary)
