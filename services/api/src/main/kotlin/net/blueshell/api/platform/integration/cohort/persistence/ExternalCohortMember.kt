package net.blueshell.api.platform.integration.cohort.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDateTime

@Entity
@Table(name = "external_cohort_member")
data class ExternalCohortMember(
    @EmbeddedId val id: ExternalCohortMemberId,
    val label: String?,
    @Column(nullable = false) val observedAt: LocalDateTime,
)

@Embeddable
data class ExternalCohortMemberId(
    val cohortId: Long,
    val externalUserId: String,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
