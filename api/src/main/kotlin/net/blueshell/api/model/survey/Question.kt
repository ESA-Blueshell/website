package net.blueshell.api.model.survey

import jakarta.persistence.*
import lombok.Data
import lombok.EqualsAndHashCode
import lombok.NoArgsConstructor
import lombok.ToString
import net.blueshell.api.base.DirtyAwareModel
import net.blueshell.api.base.JpaListener
import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.common.hibernate.DirtyField
import net.blueshell.api.common.hibernate.DirtyModel
import net.blueshell.api.model.converter.StringListConverter
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "questions",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_questions_survey_idx_deleted_at",
        columnNames = ["survey_id", "idx", "deleted_at"]
    )],
    indexes = [Index(
        name = "idx_questions_deleted_at",
        columnList = "deleted_at"
    ), Index(name = "idx_questions_survey_id", columnList = "survey_id"), Index(
        name = "idx_questions_survey_idx",
        columnList = "survey_id, idx"
    ), Index(name = "idx_questions_type", columnList = "type")]
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE questions SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@EntityListeners(JpaListener::class)
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
@DirtyModel
class Question : DirtyAwareModel() {
    @Column(name = "idx", nullable = false)
    @ToString.Include
    private var idx: Long? = null

    @Column(name = "survey_id", insertable = false, updatable = false, nullable = false)
    @ToString.Include
    private var surveyId: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id")
    private val survey: Survey? = null

    @OneToMany(mappedBy = "question", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val answers: MutableSet<Answer?>? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    @ToString.Include
    @DirtyField
    private var type: QuestionType? = null

    @Column(name = "label", nullable = false, length = 2047)
    @ToString.Include
    @DirtyField
    private var label: String? = null

    @Column(name = "choice_labels", columnDefinition = "JSON")
    @Convert(converter = StringListConverter::class)
    @DirtyField
    private var choiceLabels: MutableList<String?>? = null

    @Column(name = "answer_count", nullable = false, updatable = false, insertable = false)
    private var answerCount: Long = 0
}
