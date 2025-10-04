package net.blueshell.api.model.survey;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "surveys")
@SQLRestriction("deleted_at >= NOW()")
@SQLDelete(sql = "UPDATE surveys SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Data
public class Survey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Question> questions = new HashSet<>();

    @OneToMany
    @JoinTable(
            name = "questions",
            joinColumns = @JoinColumn(name = "survey_id"),
            inverseJoinColumns = @JoinColumn(name = "id")
    )
    @JoinColumn(name = "question_id")
    private Set<Answer> answers = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Survey survey = (Survey) o;
        return Objects.equals(id, survey.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}