package net.blueshell.api.model.event;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.model.User;
import net.blueshell.api.model.survey.Answer;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(
        name = "event_signups",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_event_signups_event_user_deleted_at",
                        columnNames = {"event_id", "user_id", "deleted_at"}
                ),
                @UniqueConstraint(
                        name = "uk_event_signups_event_guest_deleted_at",
                        columnNames = {"event_id", "guest_id", "deleted_at"}
                ),
                @UniqueConstraint(
                        name = "uk_event_signups_guest_deleted_at",
                        columnNames = {"guest_id", "deleted_at"}
                )
        },
        indexes = {
                @Index(name = "idx_event_signups_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_event_signups_event_id", columnList = "event_id"),
                @Index(name = "idx_event_signups_user_id", columnList = "user_id"),
                @Index(name = "idx_event_signups_guest_id", columnList = "guest_id"),
                @Index(name = "idx_event_signups_signed_up_at", columnList = "signed_up_at")
        }
)
@Data
@SQLDelete(sql = "UPDATE event_signups SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener.class)
public class EventSignUp implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", insertable = false, updatable = false, nullable = false)
    private Event event;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @ManyToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "user_id")
    private Long userId;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    private Guest guest;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinTable(
            name = "event_sign_up_answers",
            joinColumns = @JoinColumn(name = "event_sign_up_id"),
            inverseJoinColumns = @JoinColumn(name = "answer_id")
    )
    private Set<Answer> answers;

    @Column(name = "signed_up_at", nullable = false)
    private LocalDateTime signedUpAt;

    @Column(name = "deleted_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("9999-12-31 23:59:59")
    private Timestamp deletedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated
    private Timestamp createdAt;
}
