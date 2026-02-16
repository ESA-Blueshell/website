package net.blueshell.api.domain.user.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.shared.enums.StudyLevel
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "study_programs",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_study_programs_name_level_deleted_at", columnNames = ["name", "level", "deleted_at"])
    ],
    indexes = [
        Index(name = "idx_study_programs_level_active", columnList = "level, active")
    ]
)
@SQLDelete(sql = "UPDATE study_programs SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class StudyProgram : AuditedAutoIdEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 16)
    lateinit var level: StudyLevel

    @Column(name = "name", nullable = false, length = 255)
    lateinit var name: String

    @Column(name = "active", nullable = false)
    var active: Boolean = true
}
