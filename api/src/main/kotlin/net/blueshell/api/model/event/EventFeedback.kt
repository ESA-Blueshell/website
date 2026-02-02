package net.blueshell.api.model.event

import jakarta.persistence.*
import lombok.Data
import lombok.EqualsAndHashCode
import lombok.NoArgsConstructor
import lombok.ToString
import net.blueshell.api.base.BaseModel
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "event_feedback",
    indexes = [Index(
        name = "idx_event_feedback_deleted_at",
        columnList = "deleted_at"
    ), Index(name = "idx_event_feedback_event_id", columnList = "event_id")]
)
@SQLDelete(sql = "UPDATE event_feedback SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
class EventFeedback : BaseModel() {
    @Column(name = "feedback", nullable = false)
    private var feedback: String? = null

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private val event: Event? = null
}
