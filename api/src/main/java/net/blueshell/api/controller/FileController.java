package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.FileDTO;
import net.blueshell.api.mapper.FileMapper;
import net.blueshell.api.model.*;
import net.blueshell.api.model.event.Event;
import net.blueshell.api.model.event.EventPicture;
import net.blueshell.api.repository.FileRepository;
import net.blueshell.api.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * yoinked from <a href="https://attacomsian.com/blog/uploading-files-spring-boot">https://attacomsian.com/blog/uploading-files-spring-boot</a>
 */

@Controller
@Tag(name = "Files")
public class FileController extends BaseController<FileService, FileRepository> {

    private final FileMapper fileMapper;
    private final UserService userService;
    private final EventService eventService;
    private final MembershipService membershipService;
    private final EventPictureService eventPictureService;

    @Autowired
    public FileController(FileService service, FileRepository repository, FileMapper fileMapper, UserService userService, EventService eventService, MembershipService membershipService, EventPictureService eventPictureService) {
        super(service, repository);
        this.fileMapper = fileMapper;
        this.userService = userService;
        this.eventService = eventService;
        this.membershipService = membershipService;
        this.eventPictureService = eventPictureService;
    }

    @GetMapping("/download/{filename:.+}")
    @ResponseBody
    @PreAuthorize("hasPermission(#filename, 'File', 'read')")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        File file = service.findByName(filename);
        return service.prepareFileResponse(file);
    }

    @GetMapping("/assets/{filename:.+}")
    @ResponseBody
    @PermitAll
    public ResponseEntity<Resource> downloadAsset(@PathVariable String filename) {
        return service.prepareAssetResponse(filename);
    }

    @PostMapping("/files")
    @PreAuthorize("hasAuthority('MEMBER')")
    public List<FileDTO> uploadFile(List<FileDTO> dtos) {
        List<File> files = fileMapper.fromDTOs(dtos);
        return fileMapper.toDTOs(files);
    }

    @GetMapping("/eventPictures/{eventPictureId}")
    @ResponseBody
    @PreAuthorize("hasAuthority('MEMBER')")
    public ResponseEntity<Resource> downloadEventPicture(@PathVariable Long eventPictureId) {
        EventPicture eventPicture = eventPictureService.findById(eventPictureId);
        return service.prepareFileResponse(eventPicture.getPicture());
    }

    @GetMapping("/events/{eventId}/banner")
    @ResponseBody
    @PreAuthorize("hasPermission(#eventId, 'Event', 'read')")
    public ResponseEntity<Resource> downloadBanner(@PathVariable Long eventId) {
        Event event = eventService.findById(eventId);
        return service.prepareFileResponse(event.getBanner());
    }

    @GetMapping("/memberships/{membershipId}/signature")
    @ResponseBody
    @PreAuthorize("hasPermission(#membershipId, 'Membership', 'read')")
    public ResponseEntity<Resource> downloadSignature(@PathVariable Long membershipId) {
        Membership membership = membershipService.findById(membershipId);
        return service.prepareFileResponse(membership.getSignature());
    }

    @GetMapping("/users/{userId}/profilePicture")
    @ResponseBody
    @PreAuthorize("hasPermission(#userId, 'User', 'read')")
    public ResponseEntity<Resource> downloadProfilePicture(@PathVariable Long userId) {
        User user = userService.findById(userId);
        return service.prepareFileResponse(user.getProfilePicture());
    }
}