package net.blueshell.api.domain.event.persistence.spec

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Root
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.application.query.EventSignUpQuery
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDateTime

object EventSignUpSpecifications {
    private fun distinct(): Specification<EventSignUp> {
        return Specification { _: Root<EventSignUp>, query: CriteriaQuery<*>?, cb: CriteriaBuilder ->
            query?.distinct(true)
            cb.conjunction()
        }
    }

    @JvmOverloads
    fun approved(value: Boolean? = true): Specification<EventSignUp> {
        if (value == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            if (value)
                cb.isTrue(root.join<Any, Any>("event", JoinType.INNER).get("approved"))
            else
                cb.isFalse(root.join<Any, Any>("event", JoinType.INNER).get("approved"))
        }
    }

    fun startTimeFrom(from: LocalDateTime?): Specification<EventSignUp> {
        if (from == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            cb.greaterThanOrEqualTo(
                root.join<Any, Any>("event", JoinType.INNER).get("startTime"),
                from
            )
        }
    }

    fun startTimeTo(to: LocalDateTime?): Specification<EventSignUp> {
        if (to == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            cb.lessThanOrEqualTo(
                root.join<Any, Any>("event", JoinType.INNER).get("startTime"),
                to
            )
        }
    }

    fun timeBetween(from: LocalDateTime?, to: LocalDateTime?): Specification<EventSignUp> {
        return startTimeFrom(from).and(startTimeTo(to))
    }

    fun committeeId(committeeId: Long?): Specification<EventSignUp> {
        if (committeeId == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            cb.equal(
                root.join<Any, Any>("event", JoinType.INNER)
                    .join<Any, Any>("committee", JoinType.INNER)
                    .get<Any>("id"),
                committeeId
            )
        }
    }

    fun userId(userId: Long?): Specification<EventSignUp> {
        if (userId == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            cb.equal(
                root.get<Any>(
                    "userId"
                ), userId
            )
        }
    }

    fun eventId(eventId: Long?): Specification<EventSignUp> {
        if (eventId == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            cb.equal(
                root.join<Any, Any>("event", JoinType.INNER).get<Any>("id"),
                eventId
            )
        }
    }

    fun fromFilter(f: EventSignUpQuery, user: CurrentUser?): Specification<EventSignUp> {
        var spec = distinct() // avoid duplicates due to joins

        if (f.from != null || f.to != null) {
            spec = spec.and(timeBetween(f.from, f.to))
        }
        if (f.userId != null) {
            spec = spec.and(userId(f.userId))
        }
        if (f.committeeId != null) {
            spec = spec.and(committeeId(f.committeeId))
        }
        val isBoard = user?.let { hasAuthority(it, Role.BOARD) } == true
        val userId = user?.id
        val committeeId = f.committeeId
        if (!isBoard) {
            if (committeeId != null && userId != null) {
                spec = spec.and(approved(true).or(userInCommittee(userId, committeeId)))
            } else {
                spec = spec.and(approved(true))
            }
        }
        if (f.eventId != null) {
            spec = spec.and(eventId(f.eventId))
        }

        return spec
    }

    private fun userInCommittee(userId: Long, committeeId: Long): Specification<EventSignUp> {
        return Specification { root, q, cb ->
            val sq = q.subquery(Long::class.java)
            val cm = sq.from(CommitteeMember::class.java)
            sq.select(cb.literal(1L))
                .where(
                    cb.equal(cm.get<Any>("committee").get<Any>("id"), committeeId),
                    cb.equal(cm.get<Any>("user").get<Any>("id"), userId)
                )
            cb.exists(sq)
        }
    }

    private fun hasAuthority(user: CurrentUser, role: Role): Boolean {
        val inherited = user.roles.flatMap { it.allInheritedRoles }
        return inherited.any { it.matchesRole(role) }
    }
}
