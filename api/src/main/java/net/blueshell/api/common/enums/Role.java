package net.blueshell.api.common.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.*;

@Schema(enumAsRef = true)
public enum Role {
    ANONYMOUS("ANONYMOUS"),
    VEGAN("VEGAN"),
    GUEST("GUEST", ANONYMOUS),
    COMPANY("COMPANY"),
    MEMBER("MEMBER", GUEST),
    COMMITTEE("COMMITTEE", MEMBER),
    BOARD("BOARD", COMMITTEE),
    TREASURER("TREASURER", BOARD),
    ADMIN("ADMIN", TREASURER),
    SYSTEM("SYSTEM", ADMIN),
    ;

    @Getter
    private final String reprString;
    private final Role[] inheritedRoles;

    Role(String reprString, Role... inheritedRoles) {
        this.reprString = reprString;
        this.inheritedRoles = inheritedRoles;
    }

    public static Set<Role> allThatInherit(Role base) {
        EnumSet<Role> res = EnumSet.noneOf(Role.class);
        for (Role r : Role.values()) {
            if (r.matchesRole(base)) {
                res.add(r);
            }
        }
        return res;
    }

    public boolean matchesRole(Role role) {
        return role == this || Arrays.stream(inheritedRoles).anyMatch(r -> r.matchesRole(role));
    }

    /**
     * Search for all inherited roles of this Role.
     */
    public Set<Role> getAllInheritedRoles() {
        Set<Role> res = new HashSet<>();
        res.add(this);
        ArrayDeque<Role> unexplored = new ArrayDeque<>(List.of(inheritedRoles));
        while (!unexplored.isEmpty()) {
            Role currentRole = unexplored.remove();
            res.add(currentRole);
            unexplored.addAll(Arrays.stream(currentRole.inheritedRoles).filter(role -> !res.contains(role)).toList());
        }
        return res;
    }

    public Collection<Object> getAuthorities() {
        return Collections.singleton(getAllInheritedRoles());
    }

    public Object getName() {
        return this.reprString;
    }
}
