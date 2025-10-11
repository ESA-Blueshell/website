package net.blueshell.api.model.survey;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.model.converter.StringListConverter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.util.List;
import java.util.Set;

@Entity
@Table(
        name = "questions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_questions_survey_idx_deleted_at",
                        columnNames = {"survey_id", "idx", "deleted_at"}
                )
        },
        indexes = {
                @Index(name = "idx_questions_survey_id", columnList = "survey_id"),
                @Index(name = "idx_questions_survey_idx", columnList = "survey_id, idx"),
                @Index(name = "idx_questions_type", columnList = "type")
        }
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE questions SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Data
@EntityListeners(JpaListener.class)
public class Question implements BaseModel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idx", nullable = false)
    private Long idx;

    @Column(name = "survey_id", insertable = false, updatable = false, nullable = false)
    private Long surveyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id")
    private Survey survey;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Answer> answers;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private QuestionType type;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "choice_labels", columnDefinition = "JSON")
    @Convert(converter = StringListConverter.class)
    private List<String> choiceLabels;

    @Column(name = "answer_count", nullable = false, updatable = false, insertable = false)
    private long answerCount;

    @Column(name = "deleted_at", nullable = false, insertable=false, updatable = false)
    @ColumnDefault("9999-12-31 23:59:59")
    private Timestamp deletedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated
    private Timestamp createdAt;
}

