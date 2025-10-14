package net.blueshell.api.model.contribution;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Setter;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Set;

@Data
@Entity
@Table(
        name = "contribution_periods",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contribution_periods_start_end_deleted_at",
                        columnNames = {"start_date", "end_date", "deleted_at"}
                )
        },
        indexes = {
                @Index(name = "idx_contribution_periods_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_contribution_periods_start_date", columnList = "start_date"),
                @Index(name = "idx_contribution_periods_end_date", columnList = "end_date"),
                @Index(name = "idx_contribution_periods_list_id", columnList = "list_id")
        }
)
@SQLDelete(sql = "UPDATE contribution_periods SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener.class)
public class ContributionPeriod implements BaseModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "contributionPeriod", cascade = CascadeType.ALL)
    private Set<Contribution> contributions;

    @Column(name = "start_date", nullable = false)
    @Setter
    private Date startDate;

    @Column(name = "end_date")
    @Setter
    private Date endDate;

    @Column(name = "half_year_fee", nullable = false)
    @Setter
    private double halfYearFee;

    @Column(name = "full_year_fee", nullable = false)
    @Setter
    private double fullYearFee;

    @Column(name = "alumni_fee", nullable = false)
    @Setter
    private double alumniFee;

    @Column(name = "list_id")
    @Setter
    private Long listId;
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
        if (o == null || getClass() != o.getClass()) return false;
        ContributionPeriod that = (ContributionPeriod) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
