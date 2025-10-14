package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.common.enums.FileType;
import net.blueshell.api.dto.FileDTO;
import net.blueshell.api.mapper.FileMapper;
import net.blueshell.api.repository.FileRepository;
import net.blueshell.api.service.FileService;
import net.blueshell.api.validation.file.AllowedContentTypes;
import net.blueshell.api.validation.file.FileSize;
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

    public FileController(FileService service, FileRepository repository, FileMapper fileMapper) {
        super(service, repository);
        this.fileMapper = fileMapper;
    }

    @GetMapping("/events/banners/{bannerId}")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#bannerId, 'EventBanner', 'read')")
    public ResponseEntity<Resource> downloadEventBanner(@PathVariable("bannerId") Long bannerId) {
        var file = service.findByEventBannerId(bannerId);
        return service.prepareFileResponse(file);
    }

    @PostMapping(
            value = "/users/signature",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMMITTEE')")
    public FileDTO uploadSignature(
            @RequestPart("file")
            @NotNull(message = "File is required")
            @FileSize(max = 2 * 1024 * 1024)
            @AllowedContentTypes({"image/png"})
            MultipartFile file
    ) {
        var stored = service.storeMultipart(file, FileType.SIGNATURE);
        return fileMapper.toDTO(stored);
    }

    @PostMapping(
            value = "/events/banners",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMMITTEE')")
    public FileDTO uploadEventBanner(
            @RequestPart("file")
            @NotNull(message = "File is required")
            @FileSize(max = 2 * 1024 * 1024)
            @AllowedContentTypes({"image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif"})
            MultipartFile file
    ) {
        var stored = service.storeMultipart(file, FileType.EVENT_BANNER);
        return fileMapper.toDTO(stored);
    }
}
