package net.blueshell.api.domain.survey.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.hibernate.DirtyField
import net.blueshell.api.shared.hibernate.DirtyModel
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
@DirtyModel
class Question : DirtyAwareModel() {
    @Column(name = "idx", nullable = false)
    var idx: Long = 0

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    lateinit var survey: Survey
        internal set

    val surveyId: Long
        get() = survey.id ?: 0

    @OneToMany(mappedBy = "question", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
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
