package net.blueshell.api.model.event

import jakarta.persistence.*
import net.blueshell.api.base.entity.AuditedAutoIdEntity
import net.blueshell.api.base.JpaListener
import net.blueshell.api.model.survey.Answer
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "event_sign_up_answers",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_event_sign_up_answers_signup_answer_deleted_at",
        columnNames = ["event_sign_up_id", "answer_id", "deleted_at"]
    ), UniqueConstraint(
        name = "uk_event_sign_up_answers_answer_deleted_at",
        columnNames = ["answer_id", "deleted_at"]
    )],
    indexes = [Index(
        name = "idx_event_sign_up_answers_deleted_at",
        columnList = "deleted_at"
    ), Index(
        name = "idx_event_sign_up_answers_event_sign_up_id",
        columnList = "event_sign_up_id"
    ), Index(name = "idx_event_sign_up_answers_answer_id", columnList = "answer_id")]
)
@SQLDelete(sql = "UPDATE event_sign_up_answers SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
class EventSignUpAnswer : AuditedAutoIdEntity() {
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "event_sign_up_id", nullable = false)
    private var _eventSignUp: EventSignUp? = null
    var eventSignUp: EventSignUp
        get() = requireNotNull(_eventSignUp) { "Event sign-up is required" }
        set(value) {
            _eventSignUp = value
        }

    @field:OneToOne(fetch = FetchType.LAZY, optional = false, cascade = [CascadeType.ALL])
    @field:JoinColumn(name = "answer_id", nullable = false)
    private var _answer: Answer? = null
    var answer: Answer
        get() = requireNotNull(_answer) { "Answer is required" }
        set(value) {
            _answer = value
        }
}
