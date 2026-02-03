package net.blueshell.api.repository.spec

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import net.blueshell.api.common.enums.Role
import net.blueshell.api.controller.filter.EventFilter
import net.blueshell.api.model.User
import net.blueshell.api.model.committee.CommitteeMember
import net.blueshell.api.model.event.Event
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDateTime
import java.util.*

object EventSpecifications {
    private val log = LoggerFactory.getLogger(EventSpecifications::class.java)

    fun approved(): Specification<Event> {
        return Specification { root: Root<Event>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            cb!!.isTrue(
                root!!.get<Boolean>(
                    "approved"
                )
            )
        }
    }

    fun approved(value: Boolean): Specification<Event> {
        if (value == null) return Specification { root: Root<Event>, query: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.conjunction() }
        return Specification { root: Root<Event>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            if (value) cb!!.isTrue(
                root!!.get<Boolean>("approved")
            ) else cb!!.isFalse(root!!.get<Boolean>("approved"))
        }
    }

    fun startTimeFrom(from: LocalDateTime): Specification<Event> {
        if (from == null) return Specification { root: Root<Event>, query: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.conjunction() }
        return Specification { root: Root<Event>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            cb!!.greaterThanOrEqualTo<LocalDateTime>(
                root!!.get<LocalDateTime>("startTime"),
                from
            )
        }
    }

    fun startTimeTo(to: LocalDateTime): Specification<Event> {
        if (to == null) return Specification { root: Root<Event>, query: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.conjunction() }
        return Specification { root: Root<Event>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            cb!!.lessThanOrEqualTo<LocalDateTime>(
                root!!.get<LocalDateTime>("startTime"),
                to
            )
        }
    }

    fun timeBetween(from: LocalDateTime, to: LocalDateTime): Specification<Event> {
        return startTimeFrom(from).and(startTimeTo(to))
    }

    val isPublicEvent: Specification<Event>
        get() = Specification { root: Root<Event>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            cb!!.isFalse(
                root!!.get<Boolean>("membersOnly")
            )
        }

    fun membersOnly(value: Boolean): Specification<Event> {
        if (value == null) return Specification { root: Root<Event>, query: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.conjunction() }
        return Specification { root: Root<Event>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            if (value) cb!!.isTrue(
                root!!.get<Boolean>("membersOnly")
            ) else cb!!.isFalse(root!!.get<Boolean>("membersOnly"))
        }
    }

    fun userIsCommitteeMember(user: User): Specification<Event> {
        if (user == null || user.id == null) {
            return Specification { root: Root<Event>, q: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.disjunction() }
        }
        return Specification { root: Root<Event>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            q!!.distinct(true)
            val sq = q.subquery<Long>(Long::class.java)
            val cm = sq.from<CommitteeMember>(CommitteeMember::class.java)
            sq.select(cb!!.literal<Long>(1L))
                .where(
                    cb.equal(
                        cm.get<Any>("committee").get<Any>("id"),
                        root!!.get<Any>("committee").get<Any>("id")
                    ),
                    cb.equal(cm.get<Any>("userId"), user.id)
                )
            cb.exists(sq)
        }
    }

    fun committeeId(committeeId: Long): Specification<Event> {
        if (committeeId == null) return Specification { root: Root<Event>, query: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.conjunction() }
        return Specification { root: Root<Event>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            cb!!.equal(
                root!!.get<Any>(
                    "committeeId"
                ), committeeId
            )
        }
    }

    fun titleContains(text: String): Specification<Event> {
        if (text == null) return Specification { root: Root<Event>, query: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.conjunction() }
        return Specification { root: Root<Event>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            cb!!.like(
                cb.lower(root!!.get<String>("title")), "%" + text.lowercase(
                    Locale.default
                ) + "%"
            )
        }
    }

    fun fromFilter(f: EventFilter, user: User): Specification<Event> {
        var spec =
            Specification { root: Root<Event>, query: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.conjunction() }

        if (f.from != null) {
            spec = spec.and(startTimeFrom(f.from))
        }
        if (f.to != null) {
            spec = spec.and(startTimeTo(f.to))
        }
        if (f.approved != null) {
            spec = spec.and(approved(f.approved))
        }
        if (f.committeeId != null) {
            spec = spec.and(committeeId(f.committeeId))
        }
        if (f.titleContains != null && !f.titleContains.isBlank()) {
            spec = spec.and(titleContains(f.titleContains))
        }

        // Select the events that are visible to the user
        // Board members can see all events
        // So we don't filter further
        if (user == null || !user.hasAuthority(Role.MEMBER)) {
            log.info("User {} has no member role", user)
            // Only approved are visible
            spec = spec.and(approved())
        } else if (!user.hasAuthority(Role.BOARD)) {
            // For a regular member, non-public events of their committee are included
            // And approved events are included
            spec = spec.and(approved().or(userIsCommitteeMember(user)))
            log.info("User {} has member role", user)
        }

        return spec
    }
}
