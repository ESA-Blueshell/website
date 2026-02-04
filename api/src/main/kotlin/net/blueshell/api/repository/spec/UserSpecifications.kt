package net.blueshell.api.repository.spec

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Root
import net.blueshell.api.common.enums.Role
import net.blueshell.api.controller.filter.UserFilter
import net.blueshell.api.model.User
import net.blueshell.api.model.event.Event
import org.springframework.data.jpa.domain.Specification
import java.util.*

object UserSpecifications {
    fun approved(): Specification<Event> {
        return Specification { root: Root<Event>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            cb!!.isTrue(
                root!!.get<Boolean>(
                    "approved"
                )
            )
        }
    }

    fun hasMemberAuthority(): Specification<User> {
        return hasAuthorityAtLeast(Role.MEMBER)
    }

    /**
     * Generic version: users that have `base` or any role that inherits `base`.
     */
    fun hasAuthorityAtLeast(base: Role): Specification<User> {
        val allowed: MutableSet<Role> = Role.allThatInherit(base)

        return Specification { root: Root<User>, query: CriteriaQuery<*>, cb: CriteriaBuilder ->
            // Avoid duplicates when joining element collections
            query!!.distinct(true)

            // Join the ElementCollection<ROLE>
            val rolesJoin = root!!.join<User, Role>("roles", JoinType.INNER)
            rolesJoin.`in`(allowed)
        }
    }

    fun hasMemberRole(hasMemberRole: Boolean): Specification<User> {
        return Specification { root: Root<User>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            q!!.distinct(true)
            val rolesJoin = root!!.join<Any, Any>("roles", JoinType.INNER)
            if (hasMemberRole) {
                return@Specification rolesJoin.`in`(EnumSet.of<Role>(Role.MEMBER))
            } else {
                return@Specification cb!!.not(rolesJoin.`in`(EnumSet.of<Role>(Role.MEMBER)))
            }
        }
    }


    fun fromFilter(f: UserFilter, user: User?): Specification<User> {
        var spec =
            Specification { root: Root<User>, query: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.conjunction() }

        if (f.isMember != null) {
            spec = spec.and(hasMemberRole(f.isMember))
        }

        return spec
    }
}
