package net.blueshell.common.identity;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.common.enums.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
public abstract class UserDetailsProvider {
    protected SharedUserDetails getPrincipal() {
        Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("principal: {}", obj);
        if (obj instanceof SharedUserDetails) {
            return (SharedUserDetails) obj;
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
