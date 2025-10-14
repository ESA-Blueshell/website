package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.common.enums.MemberType;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.time.LocalDate;

@Entity
@Table(
        name = "memberships",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_memberships_user_id_deleted_at",
                        columnNames = {"user_id", "deleted_at"}
                ),
                @UniqueConstraint(
                        name = "uk_memberships_signature_deleted_at",
                        columnNames = {"signature_id", "deleted_at"}
                )
        },
        indexes = {
                @Index(name = "idx_memberships_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_memberships_user_id", columnList = "user_id"),
                @Index(name = "idx_memberships_start_date", columnList = "start_date"),
                @Index(name = "idx_memberships_end_date", columnList = "end_date"),
                @Index(name = "idx_memberships_member_type", columnList = "type"),
                @Index(name = "idx_memberships_incasso", columnList = "incasso"),
                @Index(name = "idx_memberships_city", columnList = "city"),
                @Index(name = "idx_memberships_country", columnList = "country")
        }
)
@SQLDelete(sql = "UPDATE memberships SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Data
@EntityListeners(JpaListener.class)
public class Membership implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private User user;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "country")
    private String country;

    @Column(name = "city")
    private String city;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private MemberType memberType;

    @JoinColumn(name = "signature_id", updatable = false, insertable = false)
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private File signature;

    @Column(name = "incasso", nullable = false)
    private boolean incasso;

    @Column(name = "deleted_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("9999-12-31 23:59:59")
    private Timestamp deletedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated
    private Timestamp createdAt;
}
