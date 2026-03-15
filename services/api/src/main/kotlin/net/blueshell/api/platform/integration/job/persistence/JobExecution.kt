package net.blueshell.api.platform.integration.job.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.shared.tracking.Actor
import java.time.Instant
import net.blueshell.api.shared.job.JobExecution as JobExecutionInterface

/**
 * Platform implementation of JobExecution with persistence and tracking capabilities.
 * Implements the shared JobExecution interface for domain layer usage.
 */
@Entity
@Table(
    name = "job_executions",
    indexes = [
        Index(name = "idx_job_executions_status", columnList = "status"),
        Index(name = "idx_job_executions_job_type", columnList = "job_type"),
        Index(name = "idx_job_executions_created_at", columnList = "created_at"),
        Index(name = "idx_job_executions_dedup", columnList = "job_type, dedup_key, status"),
    ]
)
class JobExecution(
    @Column(name = "job_type", nullable = false)
    override var jobType: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: JobExecutionStatus = JobExecutionStatus.QUEUED,

    @Lob
    @Column
    override var payload: String? = null,

    @Lob
    @Column(name = "error_message")
    var errorMessage: String? = null,

    @Column(name = "error_type")
    var errorType: String? = null,

    @Lob
    @Column(name = "error_reason")
    var errorReason: String? = null,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "queued_at")
    var queuedAt: Instant? = null,

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "finished_at")
    var finishedAt: Instant? = null,

    @Column(name = "dedup_key")
    var dedupKey: String? = null,

    @Column(name = "initiated_by_user_id")
    var initiatedByUserId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "initiated_by_type", nullable = false)
    var initiatedByType: ActionActorType = ActionActorType.SYSTEM,

    @Enumerated(EnumType.STRING)
    @Column(name = "initiated_by_role", nullable = false)
    var initiatedByRole: Role = Role.ADMIN
) : AuditedAutoIdEntity(), JobExecutionInterface

{
    @get:Transient
    override val actor: Actor
        get() = Actor(
            userId = initiatedByUserId,
            type = initiatedByType,
            role = initiatedByRole
        )
}
