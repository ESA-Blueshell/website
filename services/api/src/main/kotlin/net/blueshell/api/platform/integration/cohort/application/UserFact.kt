package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind

/**
 * One fact held by a user, matched against [CohortRule] rows by
 * `(kind, key)`. The interpretation of `key` is per-kind — see
 * [CohortFactKind].
 */
data class UserFact(val kind: CohortFactKind, val key: String)
