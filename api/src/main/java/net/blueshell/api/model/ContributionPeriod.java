package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Setter;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

@Data
@Entity
@Table(name = "contribution_periods")
@SQLDelete(sql = "UPDATE contribution_periods SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ContributionPeriod  implements BaseModel<Long> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "contributionPeriod", cascade = CascadeType.ALL)
    private Set<Contribution> contributions;

    @Column(name = "start_date")
    @Setter
    private LocalDate startDate;

    @Column(name = "end_date")
    @Setter
    private LocalDate endDate;

    @Column(name = "half_year_fee")
    @Setter
    private double halfYearFee;

    @Column(name = "full_year_fee")
    @Setter
    private double fullYearFee;

    @Column(name = "alumni_fee")
    @Setter
    private double alumniFee;

    @Column(name = "list_id")
    @Setter
    private Long listId;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

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
