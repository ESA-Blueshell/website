package net.blueshell.api.cohort.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/** One applied folder tidy, and who applied it. */
@Entity
@Table(name = "folder_tidy")
class AppliedTidy(
    @Column(name = "system", nullable = false, length = 32)
    val system: String,
    @Column(name = "moved", nullable = false)
    val moved: Int,
    @Column(name = "failed", nullable = false)
    val failed: Int,
    /** Null when the api applied it on its own behalf. */
    @Column(name = "applied_by")
    val appliedBy: Long?,
    @Column(name = "applied_at", nullable = false)
    val appliedAt: Instant,
) : AutoIdEntity()

interface AppliedTidyRepository : JpaRepository<AppliedTidy, Long> {
    fun findFirstBySystemOrderByAppliedAtDesc(system: String): AppliedTidy?
}
