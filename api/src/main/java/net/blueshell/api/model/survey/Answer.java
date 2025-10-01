package net.blueshell.api.model.survey;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.model.converter.BooleanListConverter;
import net.blueshell.api.model.event.EventSignUp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.List;

@Entity
@Table(
        name = "answers",
        indexes = {
                @Index(name = "ix_answers_question_id", columnList = "question_id"),
                @Index(name = "ix_answers_survey_id", columnList = "survey_id"),
                @Index(name = "ix_answers_user_id", columnList = "user_id")
        }
)
@SQLRestriction("deleted_at >= NOW()")
@SQLDelete(sql = "UPDATE answers SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Data
public class Answer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private Question question;

    @Column(name = "survey_id", nullable = false, insertable = false, updatable = false)
    private Long surveyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", insertable = false, updatable = false)
    private Survey survey;

    @Column(name = "event_sign_up_id", nullable = false, insertable = false, updatable = false)
    private Long eventSignUpId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_sign_up_id", insertable = false, updatable = false)
    private EventSignUp eventSignUp;

    @Column(name = "option_selections", columnDefinition = "JSON")
    @Convert(converter = BooleanListConverter.class)
    private List<Boolean> optionSelections;

    @Column(name = "text_response")
    private String textResponse;
}
