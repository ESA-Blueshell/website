package net.blueshell.api.domain.event.persistence.spec

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.application.query.EventQuery
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDateTime
import java.util.*

object EventSpecifications {
    private val log = LoggerFactory.getLogger(EventSpecifications::class.java)

    fun approved(): Specification<Event> {
        return Specification { root, _, cb ->
            cb.isTrue(
                root.get(
                    "approved"
                )
            )
        }
    }

    fun approved(value: Boolean?): Specification<Event> {
        if (value == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            if (value) cb.isTrue(
                root.get("approved")
            ) else cb.isFalse(root.get("approved"))
        }
    }

    fun startTimeFrom(from: LocalDateTime?): Specification<Event> {
        if (from == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            cb.greaterThanOrEqualTo(
                root.get("startTime"),
                from
            )
        }
    }

    fun startTimeTo(to: LocalDateTime?): Specification<Event> {
        if (to == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            cb.lessThanOrEqualTo(
                root.get("startTime"),
                to
            )
        }
    }

    fun timeBetween(from: LocalDateTime?, to: LocalDateTime?): Specification<Event> {
        return startTimeFrom(from).and(startTimeTo(to))
    }

    val isPublicEvent: Specification<Event>
        get() = Specification { root, _, cb ->
            cb.isFalse(
                root.get("membersOnly")
            )
        }

    fun membersOnly(value: Boolean?): Specification<Event> {
        if (value == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            if (value) cb.isTrue(
                root.get("membersOnly")
            ) else cb.isFalse(root.get("membersOnly"))
        }
    }

    fun userIsCommitteeMember(userId: Long): Specification<Event> {
        if (userId <= 0) {
            return Specification { _, _, cb -> cb.disjunction() }
        }
        return Specification { root, q, cb ->
            q.distinct(true)
            val sq = q.subquery(Long::class.java)
            val cm = sq.from(CommitteeMember::class.java)
            sq.select(cb.literal(1L))
                .where(
                    cb.equal(
                        cm.get<Any>("committee").get<Any>("id"),
                        root.get<Any>("committee").get<Any>("id")
                    ),
                    cb.equal(cm.get<Any>("userId"), userId)
                )
            cb.exists(sq)
        }
    }

    fun committeeId(committeeId: Long?): Specification<Event> {
        if (committeeId == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            cb.equal(
                root.get<Any>(
                    "committeeId"
                ), committeeId
            )
        }
    }

    fun titleContains(text: String?): Specification<Event> {
        if (text == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            cb.like(
                cb.lower(root.get("title")), "%" + text.lowercase(
                    Locale.getDefault()
                ) + "%"
            )
        }
    }

    fun fromFilter(f: EventQuery, user: CurrentUser?): Specification<Event> {
        var spec = Specification { _: Root<Event>, _: CriteriaQuery<*>, cb: CriteriaBuilder -> cb.conjunction() }

        val from = f.from
        if (from != null) {
            spec = spec.and(startTimeFrom(from))
        }
        val to = f.to
        if (to != null) {
            spec = spec.and(startTimeTo(to))
        }
        val approved = f.approved
        if (approved != null) {
            spec = spec.and(approved(approved))
        }
        val committeeId = f.committeeId
        if (committeeId != null) {
            spec = spec.and(committeeId(committeeId))
        }
        val titleContains = f.titleContains
        if (!titleContains.isNullOrBlank()) {
            spec = spec.and(titleContains(titleContains))
        }

        // Select the events that are visible to the user
        // Board members can see all events
        // So we don't filter further
        if (user == null || !hasAuthority(user, Role.MEMBER)) {
            log.info("User {} has no member role", user)
            // Only approved are visible
            spec = spec.and(approved())
        } else if (!hasAuthority(user, Role.BOARD)) {
            // For a regular member, non-public events of their committee are included
            // And approved events are included
            spec = spec.and(approved().or(userIsCommitteeMember(user.id)))
            log.info("User {} has member role", user)
        }

        return spec
    }

    private fun hasAuthority(user: CurrentUser, role: Role): Boolean {
        val inherited = user.roles.flatMap { it.allInheritedRoles }
        return inherited.any { it.matchesRole(role) }
    }
}
