package net.blueshell.api.permission;

import net.blueshell.api.base.BasePermissionEvaluator;
import net.blueshell.api.model.event.EventSignUp;
import net.blueshell.api.model.event.Guest;
import net.blueshell.api.service.GuestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class GuestPermission extends BasePermissionEvaluator<Guest, GuestService> {


    @Autowired
    public GuestPermission(GuestService service) {
        super(service);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, String permission) {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false;
        }

        Guest guest = (Guest) targetDomainObject;

        EventSignUp signUp = guest.getEventSignUp();
        return switch (permission) {
            case "read", "write", "delete" -> signUp != null;
            default -> false;
        };
    }

    @Override
    public boolean hasPermissionId(Authentication authentication, Object accessToken, String permission) {
        if (authentication == null || accessToken == null || permission == null) {
            return false;
        }
        Guest guest = service.findByAccessToken((String) accessToken);
        return guest != null && hasPermission(authentication, guest, permission);
    }
}
