package net.blueshell.api.model.survey;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.model.converter.BooleanListConverter;
import net.blueshell.api.model.event.EventSignUpAnswer;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.List;

@Entity
@Table(
        name = "answers",
        indexes = {
                @Index(name = "idx_answers_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_answers_question_id", columnList = "question_id")
        }
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE answers SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@EntityListeners(JpaListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Answer extends BaseModel {
    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private Question question;

    @Column(name = "option_selections", columnDefinition = "JSON")
    @Convert(converter = BooleanListConverter.class)
    private List<Boolean> optionSelections;

    @Column(name = "text_response")
    private String textResponse;

    @OneToOne(mappedBy = "answer", cascade = CascadeType.ALL)
    private EventSignUpAnswer eventSignUpAnswer;
}
