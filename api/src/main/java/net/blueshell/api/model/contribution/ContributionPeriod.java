package net.blueshell.api.model.contribution;

import jakarta.persistence.*;
import lombok.*;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.common.event.jpa.JpaListener;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.util.Set;

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
@SQLDelete(sql = "UPDATE contribution_periods SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener.class)
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
public class ContributionPeriod extends BaseModel {
    @OneToMany(mappedBy = "contributionPeriod", cascade = CascadeType.ALL)
    private Set<Contribution> contributions;

    @Column(name = "start_date", nullable = false)
    @Setter
    @ToString.Include
    private LocalDate startDate;

    @Column(name = "end_date")
    @Setter
    @ToString.Include
    private LocalDate endDate;

    @Column(name = "half_year_fee", nullable = false)
    @Setter
    @ToString.Include
    private double halfYearFee;

    @Column(name = "full_year_fee", nullable = false)
    @Setter
    @ToString.Include
    private double fullYearFee;

    @Column(name = "alumni_fee", nullable = false)
    @Setter
    @ToString.Include
    private double alumniFee;

    @Column(name = "list_id")
    @Setter
    @ToString.Include
    private Long listId;
}
