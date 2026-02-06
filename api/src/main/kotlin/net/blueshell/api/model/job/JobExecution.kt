package net.blueshell.api.model.job

import jakarta.persistence.*
import net.blueshell.api.model.base.AuditedVersionedEntity
import java.time.Instant

@Entity
@Table(
    name = "job_executions",
    indexes = [
        Index(name = "idx_job_executions_status", columnList = "status"),
        Index(name = "idx_job_executions_job_type", columnList = "job_type"),
        Index(name = "idx_job_executions_created_at", columnList = "created_at"),
    ]
)
class JobExecution(
    @Column(name = "job_type", nullable = false)
    var jobType: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: JobExecutionStatus = JobExecutionStatus.QUEUED,

    @Lob
    @Column
    var payload: String? = null,

    @Lob
    @Column(name = "error_message")
    var errorMessage: String? = null,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "queued_at")
    var queuedAt: Instant? = null,

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "finished_at")
    var finishedAt: Instant? = null
) : AuditedVersionedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
