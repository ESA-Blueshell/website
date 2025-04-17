package net.blueshell.common.identity;

import net.blueshell.common.enums.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.Set;

public abstract class IdentityProvider {
    protected Identity getIdentity() {
        Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (obj instanceof Identity) {
            return (Identity) obj;
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
        if (getIdentity() == null) {
            return new HashSet<>();
        }
        return getIdentity().getRoles();
    }
}
