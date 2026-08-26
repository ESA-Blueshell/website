package net.blueshell.api.platform.integration.cohort.persistence

import net.blueshell.api.shared.enums.CohortMemberState

/**
 * Derives a [CohortMember]'s state from its nullable fields. The enum itself lives in
 * `shared.enums` because it reaches responses; the derivation stays here, next to the entity
 * whose fields it reads.
 */
val CohortMember.state: CohortMemberState
    get() = when {
        userId == null && externalUserId.isNullOrBlank() -> CohortMemberState.INVALID
        userId == null && verifiedAt == null -> CohortMemberState.INVALID
        userId == null -> CohortMemberState.STRANGER
        verifiedAt != null && syncedAt == null -> CohortMemberState.INVALID
        verifiedAt != null -> CohortMemberState.VERIFIED
        syncedAt != null -> CohortMemberState.SYNCED
        else -> CohortMemberState.DESIRED
    }

/** A desired row still awaiting its first successful push. */
val CohortMember.needsPush: Boolean get() = state == CohortMemberState.DESIRED
