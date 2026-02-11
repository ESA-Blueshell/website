package net.blueshell.api.domain.survey.persistence

import jakarta.persistence.*
import net.blueshell.api.event.persistence.EventSignUpAnswer
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.shared.model.converter.BooleanListConverter
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
class Answer : AuditedAutoIdEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    lateinit var question: Question
        internal set

    val questionId: Long
        get() = question.id ?: 0

    @Column(name = "option_selections", columnDefinition = "JSON")
    @Convert(converter = BooleanListConverter::class)
    var optionSelections: MutableList<Boolean>? = null

    @Column(name = "text_response")
    var textResponse: String? = null

    @OneToOne(mappedBy = "answer", cascade = [CascadeType.ALL])
    var eventSignUpAnswer: EventSignUpAnswer? = null
        internal set
}
