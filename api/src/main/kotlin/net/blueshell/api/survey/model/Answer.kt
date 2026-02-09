package net.blueshell.api.survey.model

import jakarta.persistence.*
import net.blueshell.api.shared.jpa.JpaListener
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.shared.model.converter.BooleanListConverter
import net.blueshell.api.event.model.EventSignUpAnswer
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "answers",
    indexes = [
        Index(name = "idx_answers_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_answers_question_id", columnList = "question_id")
    ]
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE answers SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@EntityListeners(JpaListener::class)
class Answer : AuditedAutoIdEntity() {
    @field:Column(name = "question_id", nullable = false, updatable = false, insertable = false)
    var questionId: Long = 0
        get() = requireNotNull(_question?.id) { "Question ID is required" }
        set(value) {
            field = value
            // Only override the reference, if the ref exists and is different from current
            if (value != 0L && value != _question?.id) {
                _question = Question::class.asRef(value)
            }
        }

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "question_id", nullable = false)
    private var _question: Question? = null
    var question: Question?
        get() = _question
        set(value) {
            _question = value
            questionId = value?.id ?: questionId
        }

    @Column(name = "option_selections", columnDefinition = "JSON")
    @Convert(converter = BooleanListConverter::class)
    var optionSelections: MutableList<Boolean>? = null

    @Column(name = "text_response")
    var textResponse: String? = null

    @field:OneToOne(mappedBy = "_answer", cascade = [CascadeType.ALL])
    private var _eventSignUpAnswer: EventSignUpAnswer? = null
    val eventSignUpAnswer: EventSignUpAnswer?
        get() = _eventSignUpAnswer
}
