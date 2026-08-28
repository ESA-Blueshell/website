package net.blueshell.api.user.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.model.AuditedCustomIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.sql.Date
import java.time.Instant

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

    /**
     * Whether this member's real name may be shown beside their handle on the team pages.
     *
     * Off unless the member turns it on: their name is held here to identify them, and
     * publishing it is a decision they make for themselves.
     */
    @Column(name = "name_on_team_pages", nullable = false)
    var nameOnTeamPages: Boolean = false,

) : AuditedCustomIdEntity<Long>() {
    val userId: Long?
        get() = user.id
}
