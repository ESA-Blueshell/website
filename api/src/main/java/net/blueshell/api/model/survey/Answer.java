package net.blueshell.api.model.survey;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.model.File;
import net.blueshell.api.model.converter.BooleanListConverter;
import net.blueshell.api.model.event.EventSignUp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "answers")
@SQLRestriction("deleted_at >= NOW()")
@SQLDelete(sql = "UPDATE answers SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Data
public class Answer {
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
}
