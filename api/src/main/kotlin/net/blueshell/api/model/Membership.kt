package net.blueshell.api.model

import jakarta.persistence.*
import lombok.Data
import lombok.EqualsAndHashCode
import lombok.NoArgsConstructor
import lombok.ToString
import net.blueshell.api.base.BaseModel
import net.blueshell.api.base.JpaListener
import net.blueshell.api.common.enums.MemberType
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate

@Entity
@Table(
    name = "memberships",
    indexes = [Index(
        name = "idx_memberships_deleted_at",
        columnList = "deleted_at"
    ), Index(name = "idx_memberships_user_id", columnList = "user_id"), Index(
        name = "idx_memberships_start_date",
        columnList = "start_date"
    ), Index(name = "idx_memberships_end_date", columnList = "end_date"), Index(
        name = "idx_memberships_member_type",
        columnList = "type"
    ), Index(name = "idx_memberships_incasso", columnList = "incasso")]
)
@SQLDelete(sql = "UPDATE memberships SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
class Membership : BaseModel() {
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private val user: User? = null

    @Column(name = "user_id", nullable = false)
    @ToString.Include
    private var userId: Long? = null

    @Column(name = "start_date", nullable = false)
    @ToString.Include
    private var startDate: LocalDate = LocalDate.now()

    @Column(name = "end_date")
    @ToString.Include
    private var endDate: LocalDate? = null

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    @ToString.Include
    private var memberType = MemberType.REGULAR

    @Column(name = "incasso", nullable = false)
    @ToString.Include
    private var incasso = false
}
