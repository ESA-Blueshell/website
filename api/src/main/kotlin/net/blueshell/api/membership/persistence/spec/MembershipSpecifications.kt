package net.blueshell.api.membership.persistence.spec

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import net.blueshell.api.membership.domain.model.Membership
import net.blueshell.api.user.domain.model.User
import net.blueshell.api.membership.domain.model.filter.MembershipFilter
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate

object MembershipSpecifications {
    fun timeOverlap(from: LocalDate?, to: LocalDate?): Specification<Membership> {
        return Specification { root: Root<Membership>, q: CriteriaQuery<*>, cb: CriteriaBuilder ->
            if (from == null && to == null) {
                return@Specification cb!!.conjunction()
            }
            var f = from
            var t = to
            if (f != null && t != null && t.isBefore(f)) {
                val tmp: LocalDate = f
                f = t
                t = tmp
            }

            val start = root!!.get<LocalDate>("startDate")
            val end = root.get<LocalDate>("endDate")

            val ands: MutableList<Predicate> = ArrayList(2)

            if (t != null) {
                ands.add(cb!!.lessThanOrEqualTo(start, t))
            }

            if (f != null) {
                ands.add(cb!!.or(cb.isNull(end), cb.greaterThanOrEqualTo(end, f)))
            }
            cb!!.and(*ands.toTypedArray<Predicate>())
        }
    }

    fun fromFilter(f: MembershipFilter, user: User?): Specification<Membership> {
        var spec =
            Specification { root: Root<Membership>, query: CriteriaQuery<*>, cb: CriteriaBuilder -> cb!!.conjunction() }

        if (f.from != null || f.to != null) {
            spec = spec.and(timeOverlap(f.from, f.to))
        }

        return spec
    }
}
