package net.blueshell.api.repository.spec;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import lombok.NoArgsConstructor;
import net.blueshell.api.controller.filter.MembershipFilter;
import net.blueshell.api.model.Membership;
import net.blueshell.api.model.User;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public final class MembershipSpecifications {

    public static Specification<Membership> timeOverlap(LocalDate from, LocalDate to) {
        return (root, q, cb) -> {
            if (from == null && to == null) {
                return cb.conjunction();
            }

            LocalDate f = from;
            LocalDate t = to;
            if (f != null && t != null && t.isBefore(f)) {
                LocalDate tmp = f;
                f = t;
                t = tmp;
            }

            Path<LocalDate> start = root.get("startDate");
            Path<LocalDate> end = root.get("endDate");

            List<Predicate> ands = new ArrayList<>(2);

            if (t != null) {
                ands.add(cb.lessThanOrEqualTo(start, t));
            }

            if (f != null) {
                ands.add(cb.or(cb.isNull(end), cb.greaterThanOrEqualTo(end, f)));
            }

            return cb.and(ands.toArray(new Predicate[0]));
        };
    }

    public static Specification<Membership> fromFilter(MembershipFilter f, User user) {
        Specification<Membership> spec = (root, query, cb) -> cb.conjunction();

        if (f.getFrom() != null || f.getTo() != null) {
            spec = spec.and(timeOverlap(f.getFrom(), f.getTo()));
        }

        return spec;
    }
}
