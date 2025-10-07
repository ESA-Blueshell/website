package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.common.enums.FileType;
import net.blueshell.api.dto.FileDTO;
import net.blueshell.api.mapper.FileMapper;
import net.blueshell.api.repository.FileRepository;
import net.blueshell.api.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@Tag(name = "Files")
public class FileController extends BaseController<FileService, FileRepository> {

    private final FileMapper fileMapper;

    @Autowired
    public FileController(FileService service, FileRepository repository, FileMapper fileMapper) {
        super(service, repository);
        this.fileMapper = fileMapper;
    }

    @GetMapping("/download/{filename:.+}")
    @PreAuthorize("hasPermission(#filename, 'File', 'read')")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        var file = service.findByName(filename);
        return service.prepareFileResponse(file);
    }

    @GetMapping("/assets/{filename:.+}")
    @PermitAll
    public ResponseEntity<Resource> downloadAsset(@PathVariable String filename) {
        return service.prepareAssetResponse(filename);
    }

    @GetMapping("/events/{eventId}/banners")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#eventId, 'Event', 'read')")
    public ResponseEntity<Resource> downloadEventBanner(@PathVariable("eventId") Long eventId) {
        var file = service.findByEventId(eventId);
        return service.prepareFileResponse(file);
    }

    @PostMapping(
            value = "/events/banners",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMMITTEE')")
    public FileDTO uploadEventBanner(@RequestPart("file") MultipartFile file) {
        var stored = service.storeMultipart(file, FileType.EVENT_BANNER);
        return fileMapper.toDTO(stored);
    }
}
