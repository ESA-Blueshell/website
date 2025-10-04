package net.blueshell.api.repository.spec;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.SetJoin;
import lombok.NoArgsConstructor;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.controller.filter.EventFilter;
import net.blueshell.api.model.User;
import net.blueshell.api.model.committee.Committee;
import net.blueshell.api.model.committee.CommitteeMember;
import net.blueshell.api.model.event.Event;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

@NoArgsConstructor
public final class EventSpecifications {

    public static Specification<Event> approved() {
        return (root, q, cb) -> cb.isTrue(root.get("approved"));
    }

    public static Specification<Event> approved(Boolean value) {
        if (value == null) return (root, query, cb) -> cb.conjunction();
        return (root, q, cb) -> value ? cb.isTrue(root.get("approved")) : cb.isFalse(root.get("approved"));
    }

    public static Specification<Event> startTimeFrom(LocalDateTime from) {
        if (from == null) return (root, query, cb) -> cb.conjunction();
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("startTime"), from);
    }

    public static Specification<Event> startTimeTo(LocalDateTime to) {
        if (to == null) return (root, query, cb) -> cb.conjunction();
        return (root, q, cb) -> cb.lessThanOrEqualTo(root.get("startTime"), to);
    }

    public static Specification<Event> timeBetween(LocalDateTime from, LocalDateTime to) {
        return startTimeFrom(from).and(startTimeTo(to));
    }

    public static Specification<Event> isPublicEvent() {
        return (root, q, cb) -> cb.isFalse(root.get("membersOnly"));
    }

    public static Specification<Event> membersOnly(Boolean value) {
        if (value == null) return (root, query, cb) -> cb.conjunction();
        return (root, q, cb) -> value ? cb.isTrue(root.get("membersOnly")) : cb.isFalse(root.get("membersOnly"));
    }

    public static Specification<Event> userIsCommitteeMember(User user) {
        return (root, q, cb) -> {
            var committee = root.join("committee", JoinType.LEFT);
            SetJoin<Committee, CommitteeMember> members = committee.joinSet("members", JoinType.LEFT);
            return cb.equal(members.get("user"), user);
        };
    }

    public static Specification<Event> committeeId(Long committeeId) {
        if (committeeId == null) return (root, query, cb) -> cb.conjunction();
        return (root, q, cb) -> cb.equal(root.get("committeeId"), committeeId);
    }

    public static Specification<Event> titleContains(String text) {
        if (text == null) return (root, query, cb) -> cb.conjunction();
        return (root, q, cb) -> cb.like(cb.lower(root.get("title")), "%" + text.toLowerCase() + "%");
    }

    public static Specification<Event> fromFilter(EventFilter f, User user) {
        Specification<Event> spec = (root, query, cb) -> cb.conjunction();

        if (f.getFrom() != null || f.getTo() != null) {
            spec = spec.and(timeBetween(f.getFrom(), f.getTo()));
        }
        if (f.getApproved() != null) {
            spec = spec.and(approved(f.getApproved()));
        }
        if (f.getCommitteeId() != null) {
            spec = spec.and(committeeId(f.getCommitteeId()));
        }
        if (f.getTitleContains() != null && !f.getTitleContains().isBlank()) {
            spec = spec.and(titleContains(f.getTitleContains()));
        }

        // Select the events that are visible to the user
        // Board members can see all events
        // So we don't filter further
        if (user == null || !user.hasAuthority(Role.MEMBER)) {
            // Only approved are visible
            spec = spec.and(approved());
        } else if (!user.hasAuthority(Role.BOARD)) {
            // For a regular member, non-public events of their committee are included
            // And approved events are included
            spec = spec.and(userIsCommitteeMember(user).or(approved()));
        }

        return spec;
    }
}