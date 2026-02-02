package net.blueshell.api.model.survey

import jakarta.persistence.*
import lombok.Data
import lombok.EqualsAndHashCode
import lombok.NoArgsConstructor
import lombok.ToString
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
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
class Survey : BaseModel() {
    @OneToMany(mappedBy = "survey", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val questions: MutableSet<Question?>? = null

    @Column(name = "response_count", nullable = false, updatable = false, insertable = false)
    @ToString.Include
    private var responseCount: Long = 0
}
