package net.blueshell.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.model.converter.FormAnswerListConverter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "event_signups")
@Data
@SQLDelete(sql = "UPDATE event_signups SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at <= NOW()")
public class EventSignUp implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "event_id", insertable = false, updatable = false)
    @JsonIgnore
    private Event event;

    @Column(name = "event_id")
    private Long eventId;

    @ManyToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @JsonIgnore
    private User user;

    @Column(name = "user_id")
    private Long userId;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    private Guest guest;

    @Column(name = "form_answers", columnDefinition = "JSON")
    @Convert(converter = FormAnswerListConverter.class)
    @JsonIgnore
    private List<Object> formAnswers;

    @Column(name = "signed_up_at")
    private LocalDateTime signedUpAt;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;
}
