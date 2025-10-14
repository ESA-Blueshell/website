package net.blueshell.api.model.survey;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
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
@SQLDelete(sql = "UPDATE surveys SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Data
@EntityListeners(JpaListener.class)
public class Survey implements BaseModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Question> questions;

    @Column(name = "response_count", nullable = false, updatable = false, insertable = false)
    private long responseCount;
    @Column(name = "deleted_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("9999-12-31 23:59:59")
    private Timestamp deletedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated
    private Timestamp createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Survey other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
