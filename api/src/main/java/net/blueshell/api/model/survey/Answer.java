package net.blueshell.api.model.survey;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.common.event.jpa.JpaListener;
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
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
public class Answer extends BaseModel {
    @Column(name = "question_id", nullable = false)
    @ToString.Include
    private Long questionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private Question question;

    @Column(name = "option_selections", columnDefinition = "JSON")
    @Convert(converter = BooleanListConverter.class)
    @ToString.Include
    private List<Boolean> optionSelections;

    @Column(name = "text_response")
    @ToString.Include
    private String textResponse;

    @OneToOne(mappedBy = "answer", cascade = CascadeType.ALL)
    private EventSignUpAnswer eventSignUpAnswer;
}
