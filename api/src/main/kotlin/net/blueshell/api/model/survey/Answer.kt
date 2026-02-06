package net.blueshell.api.model.survey

import jakarta.persistence.*
import net.blueshell.api.common.jpa.JpaListener
import net.blueshell.api.model.base.AuditedAutoIdEntity
import net.blueshell.api.model.converter.BooleanListConverter
import net.blueshell.api.model.event.EventSignUpAnswer
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
open class Answer : AuditedAutoIdEntity() {
    @Column(name = "question_id", nullable = false)
    var questionId: Long = 0
        set(value) {
            field = value
            if (_question?.id != value) {
                _question = null
            }
        }

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "question_id", insertable = false, updatable = false)
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
