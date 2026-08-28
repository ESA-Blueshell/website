package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortSubjectType

/**
 * One cohort, stated in code: what it is called, where it is filed, and who belongs to it.
 *
 * This replaces a rule spelled as two columns — a fact kind and a free-form key — matched
 * against facts derived per user. Every cohort type pinned exactly one fact kind, so the two
 * enumerations were the same list written twice, and reading what a cohort meant took three
 * files. Here it takes one.
 *
 * A definition answers the same question two ways, and both are needed: [members] recomputes a
 * whole cohort, and [contains] settles one member when something about them changes. They must
 * agree, which is asserted for every definition the registry produces.
 */
interface CohortDefinition {
    /**
     * Stable identity, `TYPE:scope` — `PERIOD_MEMBERS:14`, `COMMITTEE_MEMBERS:7`,
     * `NEWSLETTER`. What a cohort record carries so it can be matched back to the code that
     * produces it, across renames of everything else.
     */
    val key: String

    val type: CohortSubjectType

    /**
     * The id of the thing this cohort is about — a contribution period, a committee — for the
     * types that are about one. Null for a cohort that stands alone, like the newsletter.
     *
     * Present so that something acting on a cohort can reach what it is about without taking
     * the key apart: making somebody a payer means writing a contribution for *that* period.
     */
    val scope: Long?

    /** What an operator sees, and what the external target is named after when created. */
    val label: String

    /** Where the target is filed on the external system, when that system files things. */
    val folder: String?

    /** Everybody who belongs, now. */
    fun members(): Set<Long>

    /** Whether this one member belongs, now. */
    fun contains(userId: Long): Boolean
}

/**
 * Produces the definitions of one type.
 *
 * A type that fans out — one cohort per contribution period, one per committee — enumerates
 * its instances here, each returned already bound to the period or committee it is about. So
 * nothing threads a scope id through the questions asked of a definition, and a definition
 * can hold its own period's dates.
 */
interface CohortDefinitionProvider {
    val type: CohortSubjectType

    fun definitions(): List<CohortDefinition>
}
