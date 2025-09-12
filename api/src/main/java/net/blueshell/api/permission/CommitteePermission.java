package net.blueshell.api.permission;

import net.blueshell.api.base.BasePermissionEvaluator;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.model.Committee;
import net.blueshell.api.service.CommitteeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CommitteePermission extends BasePermissionEvaluator<Committee, Long, CommitteeService> {

    @Autowired
    public CommitteePermission(CommitteeService service) {
        super(service);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, String permission) {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false;
        }
        var principal = getPrincipal();
        Committee committee = (Committee) targetDomainObject;
        return switch (permission) {
            case "read" -> true;
            case "write", "delete" -> principal.hasRole(Role.BOARD);
            case "createEvent" -> principal.hasRole(Role.BOARD) || committee.hasMember(principal);
            default -> false;
        };
    }

    @Override
    public boolean hasPermissionId(Authentication authentication, Object targetId, String permission) {
        if (authentication == null || targetId == null || permission == null) {
            return false;
        }
        Committee committee = service.findById((Long) targetId);
        return committee != null && hasPermission(authentication, committee, permission);
    }
}