package net.blueshell.api.model.survey

import jakarta.persistence.*
import net.blueshell.api.base.BaseModel
import net.blueshell.api.base.JpaListener
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "surveys",
    indexes = [Index(
        name = "idx_surveys_deleted_at",
        columnList = "deleted_at"
    ), Index(name = "idx_surveys_response_count", columnList = "response_count")]
)
@NamedEntityGraph(name = "Survey.withQuestions", attributeNodes = [NamedAttributeNode("questions")])
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE surveys SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@EntityListeners(JpaListener::class)
class Survey : BaseModel() {
    @OneToMany(mappedBy = "survey", cascade = [CascadeType.ALL], orphanRemoval = true)
    val questions: MutableSet<Question?>? = null

    @Column(name = "response_count", nullable = false, updatable = false, insertable = false)
    var responseCount: Long = 0
}
