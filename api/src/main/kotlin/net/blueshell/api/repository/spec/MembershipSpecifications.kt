package net.blueshell.api.repository.spec

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import lombok.NoArgsConstructor
import net.blueshell.api.controller.filter.MembershipFilter
import net.blueshell.api.model.Membership
import net.blueshell.api.model.User
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate

@NoArgsConstructor
object MembershipSpecifications {
    fun timeOverlap(from: LocalDate?, to: LocalDate?): Specification<Membership?> {
        return Specification { root: Root<Membership?>?, q: CriteriaQuery<*>?, cb: CriteriaBuilder? ->
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

            val start = root!!.get<LocalDate?>("startDate")
            val end = root.get<LocalDate?>("endDate")

            val ands: MutableList<Predicate?> = ArrayList<Predicate?>(2)

            if (t != null) {
                ands.add(cb!!.lessThanOrEqualTo<LocalDate?>(start, t))
            }

            if (f != null) {
                ands.add(cb!!.or(cb.isNull(end), cb.greaterThanOrEqualTo<LocalDate?>(end, f)))
            }
            cb!!.and(*ands.toTypedArray<Predicate?>())
        }
    }

    fun fromFilter(f: MembershipFilter, user: User?): Specification<Membership?> {
        var spec =
            Specification { root: Root<Membership?>?, query: CriteriaQuery<*>?, cb: CriteriaBuilder? -> cb!!.conjunction() }

        if (f.getFrom() != null || f.getTo() != null) {
            spec = spec.and(timeOverlap(f.getFrom(), f.getTo()))
        }

        return spec
    }
}
