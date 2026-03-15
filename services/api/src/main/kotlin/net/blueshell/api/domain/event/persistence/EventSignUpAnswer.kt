package net.blueshell.api.domain.event.persistence

import jakarta.persistence.*
import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.shared.model.AuditedSoftDeleteEntity
import net.blueshell.api.shared.model.Identifiable
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
      WHERE answer_id = ? AND event_sign_up_id = ? AND version = ?
    """
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class EventSignUpAnswer(
    @EmbeddedId
    override var id: Id = Id(),

    @MapsId("eventSignUpId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_sign_up_id", nullable = false)
    var eventSignUp: EventSignUp,

    @MapsId("answerId")
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "answer_id", nullable = false)
    var answer: Answer,
) : AuditedSoftDeleteEntity(), Identifiable<EventSignUpAnswer.Id> {
    val eventSignUpId: Long
        get() = id.eventSignUpId ?: eventSignUp.id ?: 0

    val answerId: Long
        get() = id.answerId ?: answer.id ?: 0

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
