package net.blueshell.api.domain.user.persistence.spec

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import net.blueshell.api.domain.user.application.query.MembershipQuery
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.security.CurrentUser
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate

object MembershipSpecifications {
    fun timeOverlap(from: LocalDate?, to: LocalDate?): Specification<Membership> {
        return Specification { root, _, cb ->
            if (from == null && to == null) {
                return@Specification cb.conjunction()
            }
            var f = from
            var t = to
            if (f != null && t != null && t.isBefore(f)) {
                val tmp: LocalDate = f
                f = t
                t = tmp
            }

            val start = root.get<LocalDate>("startDate")
            val end = root.get<LocalDate>("endDate")

            val ands: MutableList<Predicate> = ArrayList(2)

            if (t != null) {
                ands.add(cb.lessThanOrEqualTo(start, t))
            }

            if (f != null) {
                ands.add(cb.or(cb.isNull(end), cb.greaterThanOrEqualTo(end, f)))
            }
            cb.and(*ands.toTypedArray<Predicate>())
        }
    }

    fun fromQuery(query: MembershipQuery, user: CurrentUser?): Specification<Membership> {
        var spec = Specification { _: Root<Membership>, _: CriteriaQuery<*>?, cb: CriteriaBuilder -> cb.conjunction() }

        if (query.from != null || query.to != null) {
            spec = spec.and(timeOverlap(query.from, query.to))
        }

        if (query.userId != null) {
            spec = spec.and(Specification { root, _, cb -> cb.equal(root.get<User>("user").get<Long>("id"), query.userId) })
        }

        return spec
    }
}
