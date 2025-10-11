package net.blueshell.api.model.event;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.model.survey.Answer;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "event_sign_up_answers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_event_sign_up_answers_signup_answer_deleted_at",
                        columnNames = {"event_sign_up_id", "answer_id", "deleted_at"}
                ),
                @UniqueConstraint(
                        name = "uk_event_sign_up_answers_answer_deleted_at",
                        columnNames = {"answer_id", "deleted_at"}
                )
        },
        indexes = {
                @Index(name = "idx_event_sign_up_answers_event_sign_up_id", columnList = "event_sign_up_id"),
                @Index(name = "idx_event_sign_up_answers_answer_id", columnList = "answer_id")
        }
)
@Data
@NoArgsConstructor
@SQLDelete(sql = "UPDATE event_sign_up_answers SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
public class EventSignUpAnswer implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "event_sign_up_id", nullable = false)
    private EventSignUp eventSignUp;

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    @Column(name = "deleted_at", nullable = false, insertable=false, updatable = false)
    @ColumnDefault("9999-12-31 23:59:59")
    private Timestamp deletedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated
    private Timestamp createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventSignUpAnswer signUpAnswer = (EventSignUpAnswer) o;
        return Objects.equals(id, signUpAnswer.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
