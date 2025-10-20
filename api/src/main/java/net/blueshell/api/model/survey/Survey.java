package net.blueshell.api.model.survey;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.Set;

@Entity
@Table(
        name = "surveys",
        indexes = {
                @Index(name = "idx_surveys_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_surveys_response_count", columnList = "response_count")
        }
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE surveys SET deleted_at = NOW() WHERE id = ? AND version = ?")
@EntityListeners(JpaListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Survey extends BaseModel {
    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Question> questions;

    @Column(name = "response_count", nullable = false, updatable = false, insertable = false)
    private long responseCount;
}
