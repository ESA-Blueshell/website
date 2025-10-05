package net.blueshell.api.permission;

import net.blueshell.api.base.BasePermissionEvaluator;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.model.event.Event;
import net.blueshell.api.service.event.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class EventPermission extends BasePermissionEvaluator<Event, EventService> {

    @Autowired
    public EventPermission(EventService service) {
        super(service);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, String permission) {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false;
        }
        Event event = (Event) targetDomainObject;
        var principal = getPrincipal();
        return switch (permission) {
            case "read" -> event.isApproved() || event.getCommittee().hasMember(principal);
            case "write" -> event.getCommittee().hasMember(principal);
            case "signUp" -> event.isApproved() && (!event.isMembersOnly() || hasAuthority(Role.MEMBER));
            default -> false;
        };
    }

    @Override
    public boolean hasPermissionId(Authentication authentication, Object targetId, String permission) {
        if (authentication == null || targetId == null || permission == null) {
            return false;
        }
        Event event = service.findById((Long) targetId);
        return event != null && hasPermission(authentication, event, permission);
    }
}