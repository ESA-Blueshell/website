package net.blueshell.api.permission;

import net.blueshell.api.base.BasePermissionEvaluator;
import net.blueshell.api.model.event.EventBanner;
import net.blueshell.api.service.event.EventBannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class EventBannerPermission extends BasePermissionEvaluator<EventBanner, EventBannerService> {

    private final EventPermission eventPermission;

    @Autowired
    public EventBannerPermission(EventBannerService service, EventPermission eventPermission) {
        super(service);
        this.eventPermission = eventPermission;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, String permission) {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false;
        }
        var target = (EventBanner) targetDomainObject;
        return switch (permission) {
            case "read" -> eventPermission.hasPermission(authentication, target.getEvent(), "read");
            default -> false;
        };
    }

    @Override
    public boolean hasPermissionId(Authentication authentication, Object targetId, String permission) {
        if (authentication == null || targetId == null || permission == null) {
            return false;
        }
        var target = service.findById((Long) targetId);
        return target != null && hasPermission(authentication, target, permission);
    }
}