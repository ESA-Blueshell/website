package net.blueshell.api.platform.integration.cohort.application.definition

import net.blueshell.api.domain.user.application.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Every cohort the association has, and the one place that decides who is eligible at all.
 *
 * A soft-deleted member belongs to nothing. That is settled here rather than in each
 * definition: it is true of all of them, and a definition that forgot it would quietly keep
 * somebody on a mailing list after they asked to be gone. Their membership rows stay put —
 * they are kept for historical statistics — but no definition claims them.
 */
@Service
class CohortDefinitionRegistry(
    private val providers: List<CohortDefinitionProvider>,
    private val users: UserService,
) {
    fun all(): List<CohortDefinition> = providers.flatMap { it.definitions() }

    fun byKey(key: String): CohortDefinition? = all().firstOrNull { it.key == key }

    /** Who belongs to this cohort, with the deleted taken out. */
    @Transactional(readOnly = true)
    fun membersOf(definition: CohortDefinition): Set<Long> {
        val members = definition.members()
        if (members.isEmpty()) return emptySet()
        val deleted = users.findSoftDeletedIds(members)
        return if (deleted.isEmpty()) members else members - deleted
    }

    /**
     * Which cohorts this member belongs to. A deleted member belongs to none of them, which
     * is what turns their deletion into a removal from every external system.
     */
    @Transactional(readOnly = true)
    fun definitionsFor(userId: Long): List<CohortDefinition> {
        if (users.isSoftDeleted(userId)) return emptyList()
        return all().filter { it.contains(userId) }
    }
}
