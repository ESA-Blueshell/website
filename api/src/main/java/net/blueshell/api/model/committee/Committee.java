package net.blueshell.api.model.committee;

import jakarta.persistence.*;
import lombok.*;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.model.User;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "committees",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_committees_name_deleted_at",
                        columnNames = {"name", "deleted_at"}
                )
        },
        indexes = {
                @Index(name = "idx_committees_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_committees_name", columnList = "name")
        }
)
@SQLDelete(sql = "UPDATE committees SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
public class Committee extends BaseModel {
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false, length = 4095)
    private String description;

    @OneToMany(
            mappedBy = "committee",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private Set<CommitteeMember> members = new HashSet<>();

    public boolean hasMember(User user) {
        return getMembers().stream().anyMatch(cm -> cm.getUser().getId().equals(user.getId()));
    }
}
