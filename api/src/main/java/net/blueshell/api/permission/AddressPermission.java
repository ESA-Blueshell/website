package net.blueshell.api.permission;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BasePermissionEvaluator;
import net.blueshell.api.model.Address;
import net.blueshell.api.service.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AddressPermission extends BasePermissionEvaluator<Address, AddressService> {

    @Autowired
    public AddressPermission(AddressService service) {
        super(service);
    }

    public boolean hasPermission(Authentication authentication, Object object, String permission) {
        if (authentication == null || object == null || permission == null) {
            return false;
        }
        var target = (Address) object;
        var principal = getPrincipal();
        return switch (permission) {
            case "read", "write" -> principal.getId().equals(target.getUser().getId());
            default -> false;
        };
    }

    public boolean hasPermissionId(Authentication authentication, Object targetId, String permission) {
        if (authentication == null || targetId == null || permission == null) {
            return false;
        }

        var target = service.findById((Long) targetId);
        return target != null && hasPermission(authentication, target, permission);
    }
}
