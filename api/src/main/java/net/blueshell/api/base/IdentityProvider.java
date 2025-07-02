package net.blueshell.api.base;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
public abstract class IdentityProvider {
    protected User getPrincipal() {
        Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("OBJ: " + obj);

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
}
