package net.blueshell.api.cohort.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/** One reconcile of one target, and the drift it found. Append-only. */
@Entity
@Table(name = "target_reconcile_run")
class TargetReconcileRun(
    /** The target (a `cohort` row) that was reconciled. */
    @Column(name = "target_id", nullable = false)
    val targetId: Long,
    @Column(name = "started_at", nullable = false)
    val startedAt: Instant,
    /** What queued the reconcile; null for a run queued before runs recorded it. */
    @Enumerated(EnumType.STRING)
    @Column(name = "run_trigger", length = 32)
    val trigger: JobTrigger?,
    @Column(name = "in_sync", nullable = false)
    val inSync: Int,
    @Column(name = "ours_only", nullable = false)
    val oursOnly: Int,
    @Column(name = "theirs_only", nullable = false)
    val theirsOnly: Int,
) : AutoIdEntity()
