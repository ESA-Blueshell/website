package net.blueshell.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.util.Objects;

@Entity
@Table(name = "contributions")
@Data
@SQLDelete(sql = "UPDATE contributions SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Contribution  implements BaseModel<Long> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotFound(action = NotFoundAction.IGNORE)
    @ToString.Exclude
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @ManyToOne
    @JoinColumn(name = "member_id")
    private Membership membership;

    @Column(name = "paid")
    private Boolean paid;

    @Column(name = "reminded_at")
    private Timestamp remindedAt;

    @ManyToOne
    @JoinColumn(name = "contribution_period_id")
    private ContributionPeriod contributionPeriod;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    public Contribution(User user, ContributionPeriod contributionPeriod) {
        this.user = user;
        this.contributionPeriod = contributionPeriod;
    }

    public Contribution() {
    }

    @JsonProperty("userId")
    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contribution that = (Contribution) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
