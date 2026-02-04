package net.blueshell.api.model.survey

import jakarta.persistence.*
import net.blueshell.api.base.entity.AuditedAutoIdEntity
import net.blueshell.api.base.JpaListener
import net.blueshell.api.model.converter.BooleanListConverter
import net.blueshell.api.model.event.EventSignUpAnswer
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "answers",
    indexes = [Index(
        name = "idx_answers_deleted_at",
        columnList = "deleted_at"
    ), Index(name = "idx_answers_question_id", columnList = "question_id")]
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE answers SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@EntityListeners(JpaListener::class)
class Answer : AuditedAutoIdEntity() {
    @Column(name = "question_id", nullable = false)
    var questionId: Long = 0

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    val question: Question? = null

    @Column(name = "option_selections", columnDefinition = "JSON")
    @Convert(converter = BooleanListConverter::class)
    var optionSelections: MutableList<Boolean>? = null

    @Column(name = "text_response")
    var textResponse: String? = null

    @OneToOne(mappedBy = "answer", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val eventSignUpAnswer: EventSignUpAnswer? = null
}
