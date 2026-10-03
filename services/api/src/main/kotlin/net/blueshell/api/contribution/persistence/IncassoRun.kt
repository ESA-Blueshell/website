package net.blueshell.api.contribution.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant
import java.time.LocalDate

/**
 * One collection of a period's contributions by incasso. Its notifications are the members it
 * collects from; it waits for upload to ING until the board says it was submitted there.
 */
@Entity
@Table(name = "incasso_runs")
class IncassoRun(
    @Column(name = "contribution_period_id", nullable = false)
    val contributionPeriodId: Long,
    @Column(name = "collection_date", nullable = false)
    val collectionDate: LocalDate,
    /** Already in ING's characters, as it goes in the file. */
    @Column(name = "statement_text", nullable = false, length = 140)
    val statementText: String,
    @Column(name = "created_by")
    val createdBy: Long?,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "submitted_by")
    var submittedBy: Long? = null,
    @Column(name = "submitted_at")
    var submittedAt: Instant? = null,
) : AutoIdEntity()
