package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.*;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.common.enums.MemberType;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Entity
@Table(
        name = "memberships",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_memberships_user_id_deleted_at",
                        columnNames = {"user_id", "deleted_at"}
                )
        },
        indexes = {
                @Index(name = "idx_memberships_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_memberships_user_id", columnList = "user_id"),
                @Index(name = "idx_memberships_start_date", columnList = "start_date"),
                @Index(name = "idx_memberships_end_date", columnList = "end_date"),
                @Index(name = "idx_memberships_member_type", columnList = "type"),
                @Index(name = "idx_memberships_incasso", columnList = "incasso")
        }
)
@SQLDelete(sql = "UPDATE memberships SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener.class)
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
public class Membership extends BaseModel {
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate = LocalDate.now();

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private MemberType memberType = MemberType.REGULAR;

    @Column(name = "incasso", nullable = false)
    private boolean incasso = false;
}
