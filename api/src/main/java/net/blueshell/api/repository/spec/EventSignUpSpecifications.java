package net.blueshell.api.repository.spec;

import jakarta.persistence.criteria.JoinType;
import lombok.NoArgsConstructor;
import net.blueshell.api.base.IdentityProvider;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.controller.filter.EventSignUpFilter;
import net.blueshell.api.model.User;
import net.blueshell.api.model.event.EventSignUp;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

@NoArgsConstructor
public final class EventSignUpSpecifications extends IdentityProvider {

    private static Specification<EventSignUp> distinct() {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.conjunction();
        };
    }

    public static Specification<EventSignUp> approved() {
        return approved(true);
    }

    public static Specification<EventSignUp> approved(Boolean value) {
        if (value == null) return (root, query, cb) -> cb.conjunction();
        return (root, q, cb) -> value
                ? cb.isTrue(root.join("event", JoinType.INNER).get("approved"))
                : cb.isFalse(root.join("event", JoinType.INNER).get("approved"));
    }

    public static Specification<EventSignUp> startTimeFrom(LocalDateTime from) {
        if (from == null) return (root, query, cb) -> cb.conjunction();
        return (root, q, cb) ->
                cb.greaterThanOrEqualTo(root.join("event", JoinType.INNER).get("startTime"), from);
    }

    public static Specification<EventSignUp> startTimeTo(LocalDateTime to) {
        if (to == null) return (root, query, cb) -> cb.conjunction();
        return (root, q, cb) ->
                cb.lessThanOrEqualTo(root.join("event", JoinType.INNER).get("startTime"), to);
    }

    public static Specification<EventSignUp> timeBetween(LocalDateTime from, LocalDateTime to) {
        return startTimeFrom(from).and(startTimeTo(to));
    }

    public static Specification<EventSignUp> committeeId(Long committeeId) {
        if (committeeId == null) return (root, query, cb) -> cb.conjunction();
        return (root, q, cb) ->
                cb.equal(root.join("event", JoinType.INNER).get("committeeId"), committeeId);
    }

    public static Specification<EventSignUp> userId(Long userId) {
        if (userId == null) return (root, query, cb) -> cb.conjunction();
        return (root, q, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<EventSignUp> eventId(Long eventId) {
        if (eventId == null) return (root, query, cb) -> cb.conjunction();
        return (root, q, cb) -> cb.equal(root.get("eventId"), eventId);
    }

    public static Specification<EventSignUp> fromFilter(EventSignUpFilter f, User user) {
        Specification<EventSignUp> spec = distinct(); // avoid duplicates due to joins

        if (f == null) return spec;

        if (f.getFrom() != null || f.getTo() != null) {
            spec = spec.and(timeBetween(f.getFrom(), f.getTo()));
        }
        if (f.getUserId() != null) {
            spec = spec.and(userId(f.getUserId()));
        }
        if (f.getCommitteeId() != null) {
            spec = spec.and(committeeId(f.getCommitteeId()));
        }
        if (!user.hasAuthority(Role.BOARD) && !user.getCommitteeIds().contains(f.getCommitteeId())) {
            spec = spec.and(approved(true));
        }
        if (f.getEventId() != null) {
            spec = spec.and(eventId(f.getEventId()));
        }

        return spec;
    }
}
