package net.blueshell.api.survey.model

import jakarta.persistence.*
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.hibernate.DirtyField
import net.blueshell.api.shared.hibernate.DirtyModel
import net.blueshell.api.shared.jpa.JpaListener
import net.blueshell.api.shared.model.DirtyAwareModel
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.shared.model.converter.StringListConverter
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
@EntityListeners(JpaListener::class)
@DirtyModel
class Question : DirtyAwareModel() {
    @Column(name = "idx", nullable = false)
    var idx: Long = 0

    @field:Column(name = "survey_id", nullable = false, updatable = false, insertable = false)
    var surveyId: Long = 0
        get() = requireNotNull(_survey?.id) { "Survey ID is required" }
        set(value) {
            field = value
            // Only override the reference, if the ref exists and is different from current
            if (value != 0L && value != _survey?.id) {
                _survey = Survey::class.asRef(value)
            }
        }

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "survey_id", nullable = false)
    private var _survey: Survey? = null
    var survey: Survey
        get() = requireNotNull(_survey) { "Survey is required" }
        set(value) {
            _survey = value
            surveyId = value.id ?: surveyId
        }

    @OneToMany(mappedBy = "_question", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _answers: MutableSet<Answer> = linkedSetOf()
    val answers: Set<Answer>
        get() = _answers

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    @DirtyField
    lateinit var type: QuestionType

    @Column(name = "label", nullable = false, length = 2047)
    @DirtyField
    lateinit var label: String

    @Column(name = "choice_labels", columnDefinition = "JSON")
    @Convert(converter = StringListConverter::class)
    @DirtyField
    var choiceLabels: MutableList<String>? = null

    @Column(name = "answer_count", nullable = false, updatable = false, insertable = false)
    var answerCount: Long = 0
}
