package net.blueshell.api.user.persistence

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Root
import net.blueshell.api.user.domain.UserQuery
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import org.springframework.data.jpa.domain.Specification
import java.util.*

object UserSpecifications {
    fun hasMemberAuthority(): Specification<User> {
        return hasAuthorityAtLeast(Role.MEMBER)
    }

    /**
     * Generic version: users that have `base` or any role that inherits `base`.
     */
    fun hasAuthorityAtLeast(base: Role): Specification<User> {
        val allowed: MutableSet<Role> = Role.allThatInherit(base)

        return Specification { root, query, _ ->
            // Avoid duplicates when joining element collections
            query.distinct(true)

            // Join the ElementCollection<ROLE>
            val rolesJoin = root.join<User, Role>("roles", JoinType.INNER)
            rolesJoin.`in`(allowed)
        }
    }

    fun hasMemberRole(hasMemberRole: Boolean): Specification<User> {
        return Specification { root, q, cb ->
            q.distinct(true)
            val rolesJoin = root.join<Any, Any>("roles", JoinType.INNER)
            if (hasMemberRole) {
                return@Specification rolesJoin.`in`(EnumSet.of(Role.MEMBER))
            } else {
                return@Specification cb.not(rolesJoin.`in`(EnumSet.of(Role.MEMBER)))
            }
        }
    }


    fun fromQuery(query: UserQuery, user: CurrentUser?): Specification<User> {
        var spec = Specification { _: Root<User>, _: CriteriaQuery<*>?, cb: CriteriaBuilder -> cb.conjunction() }

        val isMember = query.isMember
        if (isMember != null) {
            spec = spec.and(hasMemberRole(isMember))
        }

        return spec
    }
}
