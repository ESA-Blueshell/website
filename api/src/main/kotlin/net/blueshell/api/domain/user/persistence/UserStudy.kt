package net.blueshell.api.domain.user.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.enums.StudyStatus
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "user_studies",
    indexes = [
        Index(name = "idx_user_studies_user_id", columnList = "user_id"),
        Index(name = "idx_user_studies_status", columnList = "status")
    ]
)
@SQLDelete(sql = "UPDATE user_studies SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class UserStudy(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_program_id", nullable = false)
    val studyProgram: StudyProgram,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: StudyStatus,

    @Column(name = "start_year")
    var startYear: Int? = null,

    @Column(name = "graduation_year")
    var graduationYear: Int? = null

) : AuditedAutoIdEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User

    val studyProgramId: Long?
        get() = studyProgram.id
}
