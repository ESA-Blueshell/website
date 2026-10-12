package net.blueshell.api.cohort.persistence

import net.blueshell.api.shared.enums.TargetMemberState

/**
 * Derives a [TargetMember]'s state from its nullable fields. The enum itself lives in
 * `shared.enums` because it reaches responses; the derivation stays here, next to the entity
 * whose fields it reads.
 */
val TargetMember.state: TargetMemberState
    get() =
        when {
            userId == null && externalUserId.isNullOrBlank() -> TargetMemberState.INVALID
            userId == null && verifiedAt == null -> TargetMemberState.INVALID
            userId == null -> TargetMemberState.STRANGER
            verifiedAt != null && syncedAt == null -> TargetMemberState.INVALID
            verifiedAt != null -> TargetMemberState.VERIFIED
            syncedAt != null -> TargetMemberState.SYNCED
            else -> TargetMemberState.DESIRED
        }

/** A desired row still awaiting its first successful push. */
val TargetMember.needsPush: Boolean get() = state == TargetMemberState.DESIRED
