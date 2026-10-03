package net.blueshell.api.cohort.api

import net.blueshell.api.cohort.domain.CohortDefinitionRegistry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** A cohort as somebody writing to it names it, by its stable key. */
data class Audience(
    val key: String,
    val label: String,
)

/** The cohorts a module outside this one may address, such as Active members 2026-2027, and who is in each. */
@Service
class CohortAudiences(
    private val registry: CohortDefinitionRegistry,
) {
    fun all(): List<Audience> = registry.all().map { Audience(it.key, it.label) }.sortedBy { it.label }

    /** Who belongs to the cohort now, with the deleted taken out; nobody for a key no cohort has. */
    @Transactional(readOnly = true)
    fun membersOf(key: String): Set<Long> = registry.byKey(key)?.let(registry::membersOf) ?: emptySet()
}
