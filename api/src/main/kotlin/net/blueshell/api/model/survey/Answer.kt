package net.blueshell.api.model.survey

import jakarta.persistence.*
import lombok.Data
import lombok.EqualsAndHashCode
import lombok.NoArgsConstructor
import lombok.ToString
import net.blueshell.api.base.BaseModel
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
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
class Answer : BaseModel() {
    @Column(name = "question_id", nullable = false)
    @ToString.Include
    private var questionId: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private val question: Question? = null

    @Column(name = "option_selections", columnDefinition = "JSON")
    @Convert(converter = BooleanListConverter::class)
    @ToString.Include
    private var optionSelections: MutableList<Boolean?>? = null

    @Column(name = "text_response")
    @ToString.Include
    private var textResponse: String? = null

    @OneToOne(mappedBy = "answer", cascade = [CascadeType.ALL])
    private val eventSignUpAnswer: EventSignUpAnswer? = null
}
