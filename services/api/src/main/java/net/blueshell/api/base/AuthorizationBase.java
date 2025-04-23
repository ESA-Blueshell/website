package net.blueshell.api.base;

import net.blueshell.api.common.enums.Role;
import net.blueshell.api.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.Set;

public abstract class AuthorizationBase {
    protected User getPrincipal() {
        Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (obj instanceof User) {
            return (User) obj;
        }
        return null;
    }

    protected boolean hasAuthority(Role role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role.toString()));
    }


    protected Set<Role> getRoles() {
        if (getPrincipal() == null) {
            return new HashSet<>();
        }
        return getPrincipal().getRoles();
    }
}
