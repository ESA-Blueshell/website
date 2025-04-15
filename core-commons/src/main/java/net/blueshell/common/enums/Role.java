package net.blueshell.common.enums;

import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

public enum Role {
    GUEST("GUEST"),
    COMPANY("COMPANY"),
    MEMBER("MEMBER", GUEST),
    VEGAN("VEGAN"),
    COMMITTEE("COMMITTEE", MEMBER),
    BOARD("BOARD", COMMITTEE),
    ADMIN("ADMIN", BOARD),
    ;

    @Getter
    private final String reprString;
    private final Role[] inheritedRoles;

    Role(String reprString, Role... inheritedRoles) {
        this.reprString = reprString;
        this.inheritedRoles = inheritedRoles;
    }

    public boolean matchesRole(Role role) {
        return role == this || Arrays.stream(inheritedRoles).anyMatch(r -> r.matchesRole(role));
    }

    /**
     * Depth- (or breadth idk) first search for all inherited roles of this Role.
     */
    public Set<Role> getAllInheritedRoles() {
        Set<Role> res = new HashSet<>();
        res.add(this);
        ArrayDeque<Role> unexplored = new ArrayDeque<>(List.of(inheritedRoles));
        while (!unexplored.isEmpty()) {
            Role currentRole = unexplored.remove();
            res.add(currentRole);
            unexplored.addAll(Arrays.stream(currentRole.inheritedRoles).filter(role -> !res.contains(role)).collect(Collectors.toList()));
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
