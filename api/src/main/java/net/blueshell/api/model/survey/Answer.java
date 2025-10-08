package net.blueshell.api.model.survey;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.model.converter.BooleanListConverter;
import net.blueshell.api.model.event.EventSignUp;
import net.blueshell.api.model.event.EventSignUpAnswer;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.util.List;

@Entity
@Table(name = "answers")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE answers SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Data
public class Answer implements BaseModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Answer other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Column(name = "deleted_at", nullable = false)
    private Timestamp deletedAt = Timestamp.valueOf("9999-12-31 23:59:59");
}
