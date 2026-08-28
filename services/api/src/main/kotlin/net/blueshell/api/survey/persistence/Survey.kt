package net.blueshell.api.survey.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "surveys",
    indexes = [
        Index(name = "idx_surveys_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_surveys_response_count", columnList = "response_count")
    ]
)
@NamedEntityGraph(name = "Survey.withQuestions", attributeNodes = [NamedAttributeNode("_questions")])
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE surveys SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
class Survey : AuditedAutoIdEntity() {
    @OneToMany(mappedBy = "survey", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    private val _questions: MutableSet<Question> = linkedSetOf()
    val questions: Set<Question>
        get() = _questions

    @Column(name = "response_count", nullable = false, updatable = false, insertable = false)
    var responseCount: Long = 0

    fun replaceQuestions(questions: List<Question>) {
        questions.forEach { it.survey = this }
        _questions.clear()
        _questions.addAll(questions)
    }

    fun addQuestion(question: Question) {
        question.survey = this
        _questions.add(question)
    }
}
