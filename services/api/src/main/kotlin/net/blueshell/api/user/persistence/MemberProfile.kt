package net.blueshell.api.user.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AuditedCustomIdEntity
import net.blueshell.api.shared.model.SoftDelete
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.sql.Date
import java.time.Instant

@Entity
@Table(
    name = "member_profiles",
)
@SQLDelete(sql = "UPDATE member_profiles SET ${SoftDelete.STAMP}, version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction(SoftDelete.ACTIVE)
class MemberProfile(
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "id", nullable = false)
    val user: User,
    @Column(name = "date_of_birth")
    var dateOfBirth: Date? = null,
    @Column(name = "student_number")
    var studentNumber: String? = null,
    @Column(name = "gender", length = 64)
    var gender: String? = null,
    @Column(name = "nationality", length = 128)
    var nationality: String? = null,
    @Column(name = "bhv", nullable = false)
    var bhv: Boolean,
    @Column(name = "ehbo", nullable = false)
    var ehbo: Boolean,
    @Column(name = "conditions_accepted_at")
    var conditionsAcceptedAt: Instant? = null,
) : AuditedCustomIdEntity<Long>() {
    val userId: Long?
        get() = user.id

    /** The account's own answer, which the profile's forms still ask and set. */
    var nameOnRosters: Boolean
        get() = user.nameOnRosters
        set(value) {
            user.nameOnRosters = value
        }
}
