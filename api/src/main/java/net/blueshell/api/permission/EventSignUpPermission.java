package net.blueshell.api.permission;

import net.blueshell.api.base.BasePermissionEvaluator;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.model.event.EventSignUp;
import net.blueshell.api.service.event.EventService;
import net.blueshell.api.service.event.EventSignUpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class EventSignUpPermission extends BasePermissionEvaluator<EventSignUp, EventSignUpService> {

    private final EventService events;

    @Autowired
    public EventSignUpPermission(EventSignUpService service, EventService events) {
        super(service);
        this.events = events;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, String permission) {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false;
        }

        EventSignUp signUp = (EventSignUp) targetDomainObject;
        var event = events.findById(signUp.getEventId());
        var user = getPrincipal();

        return switch (permission) {
            case "read" ->
                    signUp.getUser().equals(user) || signUp.getEvent().getCommittee().hasMember(getPrincipal());
            case "write" -> event.isApproved() && (!event.isMembersOnly() || hasAuthority(Role.MEMBER));
            case "delete" -> (user != null && signUp.getUser().equals(getPrincipal()));
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