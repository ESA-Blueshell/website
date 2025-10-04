package net.blueshell.api.model.survey;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.model.converter.StringListConverter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(
        name = "questions",
        indexes = {@Index(name = "ix_questions_survey_id", columnList = "survey_id")}
)
@SQLRestriction("deleted_at >= NOW()")
@SQLDelete(sql = "UPDATE questions SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Data
public class Question implements BaseModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idx")
    private Long idx;

    @Column(name = "survey_id", insertable = false, updatable = false)
    private Long surveyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id")
    @ToString.Exclude
    private Survey survey;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<Answer> answers = new HashSet<>();

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private QuestionType type;

    @Column(name = "label")
    private String label;

    @Column(name = "choice_labels", columnDefinition = "JSON")
    @Convert(converter = StringListConverter.class)
    private List<String> choiceLabels;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Question question = (Question) o;
        return Objects.equals(id, question.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
