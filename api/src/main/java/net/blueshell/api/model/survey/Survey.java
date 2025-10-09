package net.blueshell.api.model.survey;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "surveys")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE surveys SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Data
public class Survey implements BaseModel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL)
    private Set<Question> questions;

    @Column(name = "response_count", nullable = false, updatable = false, insertable = false)
    private long responseCount;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Survey other)) return false;
        return id != null && id.equals(other.id);
    }
    @Override public int hashCode() { return getClass().hashCode(); }

    @Column(name = "deleted_at", nullable = false)
    private Timestamp deletedAt = Timestamp.valueOf("9999-12-31 23:59:59");
}
