package net.blueshell.api.model.committee;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.model.User;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "committees")
@Data
@SQLDelete(sql = "UPDATE committees SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at >= NOW()")
public class Committee implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @OneToMany(
            mappedBy = "committee",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private Set<CommitteeMember> members = new HashSet<>();


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
