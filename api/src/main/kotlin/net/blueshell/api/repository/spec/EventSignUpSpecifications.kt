package net.blueshell.api.repository.spec

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Root
import net.blueshell.api.base.IdentityProvider
import net.blueshell.api.common.enums.Role
import net.blueshell.api.controller.filter.EventSignUpFilter
import net.blueshell.api.model.User
import net.blueshell.api.model.event.EventSignUp
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDateTime

object EventSignUpSpecifications : IdentityProvider() {
    private fun distinct(): Specification<EventSignUp?> {
        return Specification { root: Root<EventSignUp?>?, query: CriteriaQuery<*>?, cb: CriteriaBuilder? ->
            query!!.distinct(true)
            cb!!.conjunction()
        }
    }

    @JvmOverloads
    fun approved(value: Boolean? = true): Specification<EventSignUp?> {
        if (value == null) return Specification { root: Root<EventSignUp?>?, query: CriteriaQuery<*>?, cb: CriteriaBuilder? -> cb!!.conjunction() }
        return Specification { root: Root<EventSignUp?>?, q: CriteriaQuery<*>?, cb: CriteriaBuilder? ->
            if (value)
                cb!!.isTrue(root!!.join<Any?, Any?>("event", JoinType.INNER).get<Boolean?>("approved"))
            else
                cb!!.isFalse(root!!.join<Any?, Any?>("event", JoinType.INNER).get<Boolean?>("approved"))
        }
    }

    fun startTimeFrom(from: LocalDateTime?): Specification<EventSignUp?> {
        if (from == null) return Specification { root: Root<EventSignUp?>?, query: CriteriaQuery<*>?, cb: CriteriaBuilder? -> cb!!.conjunction() }
        return Specification { root: Root<EventSignUp?>?, q: CriteriaQuery<*>?, cb: CriteriaBuilder? ->
            cb!!.greaterThanOrEqualTo<LocalDateTime?>(
                root!!.join<Any?, Any?>("event", JoinType.INNER).get<LocalDateTime?>("startTime"),
                from
            )
        }
    }

    fun startTimeTo(to: LocalDateTime?): Specification<EventSignUp?> {
        if (to == null) return Specification { root: Root<EventSignUp?>?, query: CriteriaQuery<*>?, cb: CriteriaBuilder? -> cb!!.conjunction() }
        return Specification { root: Root<EventSignUp?>?, q: CriteriaQuery<*>?, cb: CriteriaBuilder? ->
            cb!!.lessThanOrEqualTo<LocalDateTime?>(
                root!!.join<Any?, Any?>("event", JoinType.INNER).get<LocalDateTime?>("startTime"),
                to
            )
        }
    }

    fun timeBetween(from: LocalDateTime?, to: LocalDateTime?): Specification<EventSignUp?> {
        return startTimeFrom(from).and(startTimeTo(to))
    }

    fun committeeId(committeeId: Long?): Specification<EventSignUp?> {
        if (committeeId == null) return Specification { root: Root<EventSignUp?>?, query: CriteriaQuery<*>?, cb: CriteriaBuilder? -> cb!!.conjunction() }
        return Specification { root: Root<EventSignUp?>?, q: CriteriaQuery<*>?, cb: CriteriaBuilder? ->
            cb!!.equal(
                root!!.join<Any?, Any?>(
                    "event",
                    JoinType.INNER
                ).get<Any?>("committeeId"), committeeId
            )
        }
    }

    fun userId(userId: Long?): Specification<EventSignUp?> {
        if (userId == null) return Specification { root: Root<EventSignUp?>?, query: CriteriaQuery<*>?, cb: CriteriaBuilder? -> cb!!.conjunction() }
        return Specification { root: Root<EventSignUp?>?, q: CriteriaQuery<*>?, cb: CriteriaBuilder? ->
            cb!!.equal(
                root!!.get<Any?>(
                    "userId"
                ), userId
            )
        }
    }

    fun eventId(eventId: Long?): Specification<EventSignUp?> {
        if (eventId == null) return Specification { root: Root<EventSignUp?>?, query: CriteriaQuery<*>?, cb: CriteriaBuilder? -> cb!!.conjunction() }
        return Specification { root: Root<EventSignUp?>?, q: CriteriaQuery<*>?, cb: CriteriaBuilder? ->
            cb!!.equal(
                root!!.get<Any?>(
                    "eventId"
                ), eventId
            )
        }
    }

    fun fromFilter(f: EventSignUpFilter?, user: User): Specification<EventSignUp?> {
        var spec = distinct() // avoid duplicates due to joins

        if (f == null) return spec

        if (f.getFrom() != null || f.getTo() != null) {
            spec = spec.and(timeBetween(f.getFrom(), f.getTo()))
        }
        if (f.getUserId() != null) {
            spec = spec.and(userId(f.getUserId()))
        }
        if (f.getCommitteeId() != null) {
            spec = spec.and(committeeId(f.getCommitteeId()))
        }
        if (!user.hasAuthority(Role.BOARD) && !user.getCommitteeIds().contains(f.getCommitteeId())) {
            spec = spec.and(approved(true))
        }
        if (f.getEventId() != null) {
            spec = spec.and(eventId(f.getEventId()))
        }

        return spec
    }
}
