package net.blueshell.api.permission;

import net.blueshell.api.auth.Identity;
import net.blueshell.api.base.BasePermissionEvaluator;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.model.Event;
import net.blueshell.api.model.File;
import net.blueshell.api.model.User;
import net.blueshell.api.service.EventService;
import net.blueshell.api.service.FileService;
import net.blueshell.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class FilePermission extends BasePermissionEvaluator<File, Long, FileService> {

    private final UserService userService;
    private final EventService eventService;

    @Autowired
    public FilePermission(FileService service, UserService userService, EventService eventService) {
        super(service);
        this.userService = userService;
        this.eventService = eventService;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, String permission) {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false;
        }
        File file = (File) targetDomainObject;
        Identity principal = getPrincipal();

        if (principal.hasRole(Role.BOARD)) {
            return true;
        }

        return switch (permission) {
            case "read" -> handleReadPermission(file, principal);
            case "delete" -> handleDeletePermission(file, principal);
            default -> false;
        };
    }

    private boolean handleReadPermission(File file, Identity principal) {
        return switch (file.getFileType()) {
            case SIGNATURE -> {
                User user = userService.findBySignature(file);
                yield user != null && user.getId().equals(principal.getId());
            }
            case EVENT_BANNER -> {
                Event event = eventService.findByBanner(file);
                yield event != null && (event.isVisible() || event.getCommittee().hasMember(principal));
            }
            case EVENT_PICTURE -> principal.hasRole(Role.MEMBER);
            case PROFILE_PICTURE, DOCUMENT, SPONSOR_PICTURE -> true;
        };
    }

    private boolean handleDeletePermission(File file, Identity principal) {
        return switch (file.getFileType()) {
//            case EVENT_PICTURE -> {
//                Event event = eventService.findByPicture(file);
//                yield event != null && event.getCommittee().hasMember(principal);
//            }
            case EVENT_BANNER -> {
                Event event = eventService.findByBanner(file);
                yield event != null && event.getCommittee().hasMember(principal);
            }
            case PROFILE_PICTURE -> {
                User user = userService.findByProfilePicture(file);
                yield user != null && user.getId().equals(principal.getId());
            }
            default -> false;
        };
    }

    @Override
    public boolean hasPermissionId(Authentication authentication, Object targetId, String permission) {
        if (authentication == null || targetId == null || permission == null) {
            return false;
        }
        File file = service.findById((Long) targetId);
        return file != null && hasPermission(authentication, file, permission);
    }
}