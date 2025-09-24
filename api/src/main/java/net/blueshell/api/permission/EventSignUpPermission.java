package net.blueshell.api.permission;

import net.blueshell.api.base.BasePermissionEvaluator;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.model.EventSignUp;
import net.blueshell.api.service.EventSignUpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class EventSignUpPermission extends BasePermissionEvaluator<EventSignUp, EventSignUpService> {

    @Autowired
    public EventSignUpPermission(EventSignUpService service) {
        super(service);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, String permission) {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false;
        }

        EventSignUp signUp = (EventSignUp) targetDomainObject;
        if (getPrincipal() == null) {
            return false;
        }

        return switch (permission) {
            case "read" -> signUp.getUser().equals(getPrincipal()) || signUp.getEvent().getCommittee().hasMember(getPrincipal());
            case "write", "delete" -> signUp.getUser().equals(getPrincipal());
            default -> false;
        };
    }

    @Override
    public boolean hasPermissionId(Authentication authentication, Object targetId, String permission) {
        if (authentication == null || targetId == null || permission == null) {
            return false;
        }
        EventSignUp signUp = service.findById((Long) targetId);
        return signUp != null && hasPermission(authentication, signUp, permission);
    }
}