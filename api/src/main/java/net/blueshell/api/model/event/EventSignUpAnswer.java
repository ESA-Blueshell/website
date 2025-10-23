package net.blueshell.api.model.event;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.model.survey.Answer;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

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
                @Index(name = "idx_event_sign_up_answers_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_event_sign_up_answers_event_sign_up_id", columnList = "event_sign_up_id"),
                @Index(name = "idx_event_sign_up_answers_answer_id", columnList = "answer_id")
        }
)
@SQLDelete(sql = "UPDATE event_sign_up_answers SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener.class)
@Getter
@Setter
@NoArgsConstructor
public class EventSignUpAnswer extends BaseModel {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_sign_up_id", nullable = false)
    private EventSignUp eventSignUp;

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;
}
