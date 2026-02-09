package net.blueshell.api.model.event

import jakarta.persistence.*
import lombok.`val`
import net.blueshell.api.common.jpa.JpaListener
import net.blueshell.api.model.base.AuditedSoftDeleteEntity
import net.blueshell.api.model.base.Identifiable
import net.blueshell.api.model.base.asRef
import net.blueshell.api.model.survey.Answer
import org.hibernate.Hibernate
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "event_sign_up_answers",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_event_sign_up_answers_signup_answer_deleted_at",
            columnNames = ["event_sign_up_id", "answer_id", "deleted_at"]
        ),
        UniqueConstraint(
            name = "uk_event_sign_up_answers_answer_deleted_at",
            columnNames = ["answer_id", "deleted_at"]
        )
    ],
    indexes = [
        Index(name = "idx_event_sign_up_answers_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_event_sign_up_answers_event_sign_up_id", columnList = "event_sign_up_id"),
        Index(name = "idx_event_sign_up_answers_answer_id", columnList = "answer_id")
    ]
)
@SQLDelete(
    sql = """
      UPDATE event_sign_up_answers
      SET deleted_at = NOW(), version = version + 1
      WHERE event_sign_up_id = ? AND answer_id = ? AND version = ?
    """
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
class EventSignUpAnswer(
    @EmbeddedId
    override var id: Id = Id()
) : AuditedSoftDeleteEntity(), Identifiable<EventSignUpAnswer.Id> {

    @field:MapsId("eventSignUpId")
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "event_sign_up_id", nullable = false)
    private var _eventSignUp: EventSignUp? = null
    var eventSignUp: EventSignUp
        get() = requireNotNull(_eventSignUp) { "Event sign-up is required" }
        set(value) {
            _eventSignUp = value
            eventSignUpId = _eventSignUp?.id ?: eventSignUpId
        }

    @field:Column(name = "event_sign_up_id", nullable = false, updatable = false, insertable = false)
    var eventSignUpId: Long = 0
        get() = id.eventSignUpId ?: field
        set(value) {
            field = value
            id.eventSignUpId = value
            // Only override the reference, if the ref exists and is different from current
            if (value != 0L && value != _eventSignUp?.id) {
                _eventSignUp = EventSignUp::class.asRef(value)
            }
        }

    @field:MapsId("answerId")
    @field:OneToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "answer_id", nullable = false)
    private var _answer: Answer? = null
    var answer: Answer
        get() = requireNotNull(_answer) { "Answer is required" }
        set(value) {
            _answer = value
            answerId = _answer?.id ?: answerId
        }

    @field:Column(name = "answer_id", nullable = false, updatable = false, insertable = false)
    var answerId: Long = 0
        get() = id.answerId ?: field
        set(value) {
            field = value
            id.answerId = value
            // Only override the reference, if the ref exists and is different from current
            if (value != 0L && value != _answer?.id) {
                _answer = Answer::class.asRef(value)
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as EventSignUpAnswer
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    @Embeddable
    data class Id(
        var eventSignUpId: Long? = null,
        var answerId: Long? = null
    ) : java.io.Serializable
}
