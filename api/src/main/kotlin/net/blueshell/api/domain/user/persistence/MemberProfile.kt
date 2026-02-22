package net.blueshell.api.domain.user.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.model.AuditedCustomIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.sql.Date

@Entity
@Table(
    name = "member_profiles"
)
@SQLDelete(sql = "UPDATE member_profiles SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class MemberProfile(

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "id", nullable = false)
    val user: User,

    @Column(name = "date_of_birth", nullable = false)
    var dateOfBirth: Date,

    @Column(name = "student_number", nullable = false)
    var studentNumber: String,

    @Column(name = "gender", nullable = false, length = 64)
    var gender: String,

    @Column(name = "photo_consent", nullable = false)
    var photoConsent: Boolean,

    @Column(name = "nationality", nullable = false, length = 128)
    var nationality: String,

    @Column(name = "bhv", nullable = false)
    var bhv: Boolean,

    @Column(name = "ehbo", nullable = false)
    var ehbo: Boolean,

    ) : AuditedCustomIdEntity<Long>() {
    val userId: Long?
        get() = user.id
}
