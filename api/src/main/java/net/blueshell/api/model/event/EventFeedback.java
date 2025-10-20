package net.blueshell.api.model.event;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;

@Entity
@Table(
        name = "event_feedback",
        indexes = {
                @Index(name = "idx_event_feedback_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_event_feedback_event_id", columnList = "event_id")
        }
)
@SQLDelete(sql = "UPDATE event_feedback SET deleted_at = NOW() WHERE id = ? AND version = ?")
@Getter
@Setter
@NoArgsConstructor
public class EventFeedback extends BaseModel {
    @Column(name = "feedback", nullable = false)
    private String feedback;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
}
