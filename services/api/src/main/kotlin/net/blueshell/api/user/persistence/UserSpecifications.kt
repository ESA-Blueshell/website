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


    /**
     * Accounts that are people.
     *
     * The service account is the site itself and is nobody anybody can pick, write to or count,
     * so it is out of every listing and every total. A subquery rather than a join on roles: a
     * join would only say a row has *some* role that is not SYSTEM, which every account does.
     */
    fun isNotServiceAccount(): Specification<User> =
        Specification { root, query, cb ->
            // The query is nullable in the criteria api and never null here: this is composed
            // only for `findAll`, which supplies one for the selection and for the count. A
            // caller that somehow had none would get every account, so it is worth knowing
            // that the one path here is the one that reads the listing.
            val held = query.subquery(Long::class.java)
                ?: return@Specification cb.conjunction()
            val roles = held.correlate(root).join<User, Role>("roles", JoinType.INNER)
            held.select(cb.literal(1L)).where(roles.`in`(EnumSet.of(Role.SYSTEM)))
            cb.not(cb.exists(held))
        }

    /**
     * Accounts found by part of a name or an email address.
     *
     * Every whitespace-separated word has to match somewhere, so "roos kruk" finds Roos Kruk while
     * a single word still finds them on either half. The alternative — matching the whole term
     * against one column — cannot answer a first name and a last name typed together, which is
     * what somebody picking a member out of a list types.
     */
    fun search(value: String?): Specification<User> {
        val terms = value?.trim()?.lowercase(Locale.getDefault())?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: return Specification { _, _, cb -> cb.conjunction() }

        return Specification { root, _, cb ->
            val perTerm = terms.map { term ->
                val like = "%$term%"
                cb.or(
                    cb.like(cb.lower(root.get<String>("firstName")), like),
                    cb.like(cb.lower(root.get<String>("prefix")), like),
                    cb.like(cb.lower(root.get<String>("lastName")), like),
                    cb.like(cb.lower(root.get<String>("email")), like),
                )
            }
            cb.and(*perTerm.toTypedArray())
        }
    }

    fun fromQuery(query: UserQuery, user: CurrentUser?): Specification<User> {
        var spec = isNotServiceAccount()

        val isMember = query.isMember
        if (isMember != null) {
            spec = spec.and(hasMemberRole(isMember))
        }

        query.search?.let { spec = spec.and(search(it)) }

        return spec
    }
}
