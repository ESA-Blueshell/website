package net.blueshell.api.survey.persistence

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.shared.model.SoftDelete
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "answers",
    indexes = [
        Index(name = "idx_answers_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_answers_question_id", columnList = "question_id"),
    ],
)
@SQLRestriction(SoftDelete.ACTIVE)
@SQLDelete(sql = "UPDATE answers SET ${SoftDelete.STAMP}, version = version + 1 WHERE id = ? AND version = ?")
class Answer(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    var question: Question,
    @Column(name = "option_selections", columnDefinition = "JSON")
    @Convert(converter = BooleanListConverter::class)
    var optionSelections: MutableList<Boolean>? = null,
    @Column(name = "text_response")
    var textResponse: String? = null,
) : AuditedAutoIdEntity() {
    val questionId: Long
        get() = question.id ?: 0
}
