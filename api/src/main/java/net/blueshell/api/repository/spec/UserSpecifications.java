package net.blueshell.api.repository.spec;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.controller.filter.UserFilter;
import net.blueshell.api.model.Event;
import net.blueshell.api.model.User;
import org.springframework.data.jpa.domain.Specification;

import java.util.EnumSet;
import java.util.Set;

public class UserSpecifications {

    public static Specification<Event> visible() {
        return (root, q, cb) -> cb.isTrue(root.get("visible"));
    }

    public static Specification<User> hasMemberAuthority() {
        return hasAuthorityAtLeast(Role.MEMBER);
    }

    /**
     * Generic version: users that have `base` or any role that inherits `base`.
     */
    public static Specification<User> hasAuthorityAtLeast(Role base) {
        Set<Role> allowed = Role.allThatInherit(base);

        return (root, query, cb) -> {
            // Avoid duplicates when joining element collections
            query.distinct(true);

            // Join the ElementCollection<ROLE>
            Join<User, Role> rolesJoin = root.join("roles", JoinType.INNER);

            // role IN (:allowed)
            return rolesJoin.in(allowed);
        };
    }

    public static Specification<User> hasMemberRole(boolean hasMemberRole) {
        return (root, q, cb) -> {
            q.distinct(true);
            var rolesJoin = root.join("roles", JoinType.INNER);
            if (hasMemberRole) {
                return rolesJoin.in(EnumSet.of(Role.MEMBER));
            } else {
                return cb.not(rolesJoin.in(EnumSet.of(Role.MEMBER)));

            }
        };
    }


    public static Specification<User> fromFilter(UserFilter f, User user) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();

        if (f.getIsMember() != null) {
            spec = spec.and(hasMemberRole(f.getIsMember()));
        }

        return spec;
    }
}
