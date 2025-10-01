package net.blueshell.api.model.survey;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

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

    @OneToMany(mappedBy = "survey")
    private Set<Question> questions;

    @OneToMany(mappedBy = "survey")
    private Set<Answer> answers;
}