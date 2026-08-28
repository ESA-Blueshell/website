package net.blueshell.api.survey.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.hibernate.DirtyField
import net.blueshell.api.shared.hibernate.DirtyModel
import net.blueshell.api.shared.model.DirtyAwareModel

import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "questions",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_questions_survey_idx_deleted_at",
            columnNames = ["survey_id", "idx", "deleted_at"]
        )
    ],
    indexes = [
        Index(name = "idx_questions_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_questions_survey_id", columnList = "survey_id"),
        Index(name = "idx_questions_survey_idx", columnList = "survey_id, idx"),
        Index(name = "idx_questions_type", columnList = "type")
    ]
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE questions SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@DirtyModel
class Question(
    @Column(name = "idx", nullable = false)
    var idx: Long,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    var survey: Survey,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    @field:DirtyField
    var type: QuestionType,

    @Column(name = "label", nullable = false, length = 2047)
    @field:DirtyField
    var label: String,

    @Column(name = "choice_labels", columnDefinition = "JSON")
    @Convert(converter = StringListConverter::class)
    @field:DirtyField
    var choiceLabels: MutableList<String>? = null,

    @Column(name = "required", nullable = false)
    @field:DirtyField
    var required: Boolean = false,
) : DirtyAwareModel() {
    val surveyId: Long
        get() = survey.id ?: 0

    @OneToMany(mappedBy = "question", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _answers: MutableSet<Answer> = linkedSetOf()
    val answers: Set<Answer>
        get() = _answers

    @Column(name = "answer_count", nullable = false, updatable = false, insertable = false)
    var answerCount: Long = 0
}
