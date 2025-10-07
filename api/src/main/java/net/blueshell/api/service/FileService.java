package net.blueshell.api.service;

import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.common.enums.FileType;
import net.blueshell.api.mapper.FileMapper;
import net.blueshell.api.model.File;
import net.blueshell.api.repository.FileRepository;
import net.blueshell.api.service.event.EventBannerService;
import net.blueshell.api.service.event.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class FileService extends BaseModelService<File, FileRepository> {

    private final Path rootLocation;
    private final Path assetsLocation = Paths.get("assets");
    private final FileMapper fileMapper;
    private final EventService events;
    private final EventBannerService banners;


    @Autowired
    public FileService(
            FileRepository fileRepository,
            FileMapper fileMapper,
            EventService events,
            EventBannerService banners,
            @Value("${storage.location}") String storageLocation
    ) {
        super(fileRepository);
        this.rootLocation = Paths.get(storageLocation);
        this.fileMapper = fileMapper;
        this.events = events;
        this.banners = banners;
    }

    @Transactional(readOnly = true)
    public File findByName(String name) {
        return repository.findByName(name).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found with name: %s".formatted(name)));
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    /**
     * Store multipart file using content-hash path. Returns persisted File entity.
     */
    @Transactional
    public File storeMultipart(MultipartFile multipart, FileType type) {
        if (multipart == null || multipart.isEmpty()) {
            throw new BadRequestException("Empty file");
        }

        try {
            Files.createDirectories(rootLocation.resolve(type.getDirectory()));

            var tmp = Files.createTempFile(rootLocation, "upload-", ".tmp");
            var md = MessageDigest.getInstance("SHA-256");

            try (InputStream in = multipart.getInputStream();
                 DigestInputStream dis = new DigestInputStream(in, md);
                 OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                dis.transferTo(out);
            }
            var sha256 = HexFormat.of().formatHex(md.digest());

            var hashedFilename = fileMapper.buildHashedFilename(sha256, multipart.getOriginalFilename());
            var path = type.getDirectory() + "/" + hashedFilename;
            var fullPath = rootLocation.resolve(path).normalize();

            log.info("Storing {} at {}", multipart.getOriginalFilename(), fullPath);

            if (Files.exists(fullPath)) {
                Files.deleteIfExists(tmp);
            } else {
                try {
                    Files.move(tmp, fullPath, StandardCopyOption.ATOMIC_MOVE);
                } catch (FileAlreadyExistsException ignore) {
                    Files.deleteIfExists(tmp);
                }
            }

            var entity = repository.findByName(hashedFilename).orElse(null);
            if (entity == null) {
                entity = new File();
            }

            var mediaType = fileMapper.resolveMediaType(hashedFilename, fullPath, multipart.getContentType());
            fileMapper.populateAfterStore(entity, multipart.getOriginalFilename(), fullPath, path, mediaType);
            entity.setType(type);

            return self().create(entity);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file", e);
        } catch (NoSuchAlgorithmException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SHA-256 not available", e);
        }
    }

    private Resource loadAsResource(File file) {
        try {
            Path filePath = rootLocation.resolve(file.getPath());
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() || resource.isReadable()) return resource;
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found with name: %s".formatted(file.getName()));
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found with name: %s".formatted(file.getName()));
        }
    }

    private Resource loadAssetAsResource(String filename) {
        try {
            Path filePath = assetsLocation.resolve(filename);
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() || resource.isReadable()) return resource;
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found with name: %s".formatted(filename));
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found with name: %s".formatted(filename));
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> prepareFileResponse(File file) {
        Resource resource = loadAsResource(file);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf(file.getMediaType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.getName()).build());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.DAYS).cachePublic())
                .headers(headers)
                .body(resource);
    }

    public ResponseEntity<Resource> prepareAssetResponse(String filename) {
        Resource resource = loadAssetAsResource(filename);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf(fileMapper.detectContentTypeForAsset(filename, resource)));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.DAYS).cachePublic())
                .headers(headers)
                .body(resource);
    }

    public void deleteFile(File file) {
        var fullPath = rootLocation.resolve(file.getPath()).normalize();


        try {
            if (Files.exists(fullPath)) {
                Files.deleteIfExists(fullPath);
            }
            self().delete(file);
        } catch (IOException e) {
            log.error("Failed to delete file {}", fullPath, e);
        }
    }

    public File findByEventBannerId(Long bannerId) {
        return repository.findByEventBannerId(bannerId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Event banner not found with id: %s".formatted(bannerId)));
    }

    public void deleteFromStorage(File file) {
        var fullPath = rootLocation.resolve(file.getPath()).normalize();

        try {
            if (Files.exists(fullPath)) {
                Files.deleteIfExists(fullPath);
            }
        } catch (IOException e) {
            log.error("Failed to delete file {}", fullPath, e);
        }
    }
}
