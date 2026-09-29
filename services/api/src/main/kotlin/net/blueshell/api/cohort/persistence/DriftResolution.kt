package net.blueshell.api.cohort.persistence

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/** One person's drift on one target, resolved. Append-only. */
@Entity
@Table(name = "drift_resolution")
class DriftResolution(
    /** The target (a `cohort` row) the drift was on. */
    @Column(name = "cohort_id", nullable = false)
    val cohortId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    val action: DriftResolutionAction,
    @Column(name = "user_id")
    val userId: Long?,
    @Column(name = "external_user_id")
    val externalUserId: String?,
    /** What the target calls the person, for a row with no account. */
    @Column(name = "label")
    val label: String?,
    /** The account that resolved it; null when the api did so on its own behalf. */
    @Column(name = "resolved_by")
    val resolvedBy: Long?,
    @Column(name = "resolved_at", nullable = false)
    val resolvedAt: Instant,
) : AutoIdEntity()

@Schema(enumAsRef = true, description = "How a person's drift on a target was resolved")
enum class DriftResolutionAction {
    /** Ours only: pushed to the target. */
    PUSH,

    /** Theirs only: removed from the target. */
    REMOVE,

    /** Theirs only: the contact linked to an account. */
    LINK,

    /** Theirs only: taken in on our side, which records what the cohort stands for. */
    ADOPT,

    /** Theirs only: removed by a reconcile because the target is enforced. */
    ENFORCED_REMOVE,
}
