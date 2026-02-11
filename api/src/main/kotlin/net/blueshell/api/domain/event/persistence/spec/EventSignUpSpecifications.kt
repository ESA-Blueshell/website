package net.blueshell.api.domain.event.persistence.spec

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Root
import net.blueshell.api.auth.security.IdentityProvider
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.filter.EventSignUpFilter
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.persistence.User
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDateTime

object EventSignUpSpecifications : IdentityProvider() {
    private fun distinct(): Specification<EventSignUp> {
        return Specification { root: Root<EventSignUp>, query: CriteriaQuery<*>, cb: CriteriaBuilder ->
            query!!.distinct(true)
            cb!!.conjunction()
        }
    }

    @JvmOverloads
    fun approved(value: Boolean? = true): Specification<EventSignUp> {
        if (value == null) return Specification { root: Root<EventSignUp>, query: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.conjunction() }
        return Specification { root: Root<EventSignUp>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            if (value)
                cb!!.isTrue(root!!.join<Any, Any>("event", JoinType.INNER).get("approved"))
            else
                cb!!.isFalse(root!!.join<Any, Any>("event", JoinType.INNER).get("approved"))
        }
    }

    fun startTimeFrom(from: LocalDateTime?): Specification<EventSignUp> {
        if (from == null) return Specification { root: Root<EventSignUp>, query: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.conjunction() }
        return Specification { root: Root<EventSignUp>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            cb!!.greaterThanOrEqualTo(
                root!!.join<Any, Any>("event", JoinType.INNER).get("startTime"),
                from
            )
        }
    }

    fun startTimeTo(to: LocalDateTime?): Specification<EventSignUp> {
        if (to == null) return Specification { root: Root<EventSignUp>, query: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.conjunction() }
        return Specification { root: Root<EventSignUp>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            cb!!.lessThanOrEqualTo(
                root!!.join<Any, Any>("event", JoinType.INNER).get("startTime"),
                to
            )
        }
    }

    fun timeBetween(from: LocalDateTime?, to: LocalDateTime?): Specification<EventSignUp> {
        return startTimeFrom(from).and(startTimeTo(to))
    }

    fun committeeId(committeeId: Long?): Specification<EventSignUp> {
        if (committeeId == null) return Specification { root: Root<EventSignUp>, query: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.conjunction() }
        return Specification { root: Root<EventSignUp>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            cb!!.equal(
                root!!.join<Any, Any>(
                    "event",
                    JoinType.INNER
                ).get<Any>("committeeId"), committeeId
            )
        }
    }

    fun userId(userId: Long?): Specification<EventSignUp> {
        if (userId == null) return Specification { root: Root<EventSignUp>, query: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.conjunction() }
        return Specification { root: Root<EventSignUp>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            cb!!.equal(
                root!!.get<Any>(
                    "userId"
                ), userId
            )
        }
    }

    fun eventId(eventId: Long?): Specification<EventSignUp> {
        if (eventId == null) return Specification { root: Root<EventSignUp>, query: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.conjunction() }
        return Specification { root: Root<EventSignUp>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            cb!!.equal(
                root!!.get<Any>(
                    "eventId"
                ), eventId
            )
        }
    }

    fun fromFilter(f: EventSignUpFilter, user: User?): Specification<EventSignUp> {
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
        val isBoard = user?.hasAuthority(Role.BOARD) == true
        val inCommittee = f.committeeId != null && user?.committeeIds?.contains(f.committeeId) == true
        if (!isBoard && !inCommittee) {
            spec = spec.and(approved(true))
        }
        if (f.eventId != null) {
            spec = spec.and(eventId(f.eventId))
        }

        return spec
    }
}
