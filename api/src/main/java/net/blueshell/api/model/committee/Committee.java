package net.blueshell.api.model.committee;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.model.User;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
@Data
@SQLDelete(sql = "UPDATE committees SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
public class Committee implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false, length = 4095)
    private String description;

    @OneToMany(
            mappedBy = "committee",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private Set<CommitteeMember> members = new HashSet<>();
    @Column(name = "deleted_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("9999-12-31 23:59:59")
    private Timestamp deletedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated
    private Timestamp createdAt;

    public Committee() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Committee committee = (Committee) o;
        return id == committee.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public boolean hasMember(User user) {
        return getMembers().stream().anyMatch(cm -> cm.getUser().getId().equals(user.getId()));
    }

    public Set<User> getUsers() {
        return members == null ? Set.of() :
                members.stream().map(CommitteeMember::getUser).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
