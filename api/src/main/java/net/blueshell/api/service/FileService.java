package net.blueshell.api.service;

import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.common.exception.FileNotFoundException;
import net.blueshell.api.common.exception.StorageException;
import net.blueshell.api.config.StorageConfig;
import net.blueshell.api.model.File;
import net.blueshell.api.repository.FileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Service
public class FileService extends BaseModelService<File, Long, FileRepository> {

    private final Path rootLocation;
    private final Path assetsLocation = Paths.get("assets");

    @Value("${app.url}")
    private String appUrl;

    @Autowired
    public FileService(FileRepository fileRepository, StorageConfig properties) {
        super(fileRepository);
        this.rootLocation = Paths.get(properties.getLocation());
    }

    @Transactional(readOnly = true)
    public File findByName(String name) {
        return repository.findByName(name).orElseThrow(() ->
                new NotFoundException("File not found with name: " + name));
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new StorageException("Could not initialize storage location", e);
        }
    }

    public Path storeFile(String fileName, byte[] data) {
        Path filePath = this.rootLocation.resolve(fileName);
        if (Files.exists(filePath)) {
            throw new BadRequestException();
        }

        try (FileOutputStream outputStream = new FileOutputStream(filePath.toString())) {
            outputStream.write(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return filePath;
    }


    private Resource loadAsResource(File file) {
        try {
            Path filePath = rootLocation.resolve(file.getName());
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new FileNotFoundException(
                        "Could not read file: " + file.getName());
            }
        } catch (MalformedURLException e) {
            throw new FileNotFoundException("Could not read file: " + file.getName(), e);
        }
    }

    private Resource loadAssetAsResource(String filename) {
        try {
            Path filePath = assetsLocation.resolve(filename);
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new FileNotFoundException(
                        "Could not read asset: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new FileNotFoundException("Could not read file: " + filename, e);
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> prepareFileResponse(File file) {
        Resource resource = loadAsResource(file);

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf(file.getMediaType()));

        // Use ContentDisposition builder for proper filename handling
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.getName())
                .build();
        headers.setContentDisposition(disposition);

        // Build ResponseEntity with CacheControl
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.DAYS).cachePublic())
                .headers(headers)
                .body(resource);
    }

    public ResponseEntity<Resource> prepareAssetResponse(String filename) {
        Resource resource = loadAssetAsResource(filename);

        // Set headers
        HttpHeaders headers = new HttpHeaders();

        // Get content type from the resource filename or detect from the file
        String contentType = determineContentType(filename, resource);
        headers.setContentType(MediaType.valueOf(contentType));

        // Use ContentDisposition builder for proper filename handling
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename)
                .build();
        headers.setContentDisposition(disposition);

        // Build ResponseEntity with CacheControl
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.DAYS).cachePublic())
                .headers(headers)
                .body(resource);
    }

    private String determineContentType(String filename, Resource resource) {
        try {
            // First, try to determine content type from filename extension
            String contentType = Files.probeContentType(Paths.get(filename));
            if (contentType != null) {
                return contentType;
            }

            // If that fails, try to determine from the actual file
            resource.getFile();
            contentType = Files.probeContentType(resource.getFile().toPath());
            if (contentType != null) {
                return contentType;
            }

            // Fall back to common file extensions
            String extension = getFileExtension(filename).toLowerCase();
            return switch (extension) {
                case "jpg", "jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "svg" -> "image/svg+xml";
                case "pdf" -> "application/pdf";
                case "txt" -> "text/plain";
                case "html" -> "text/html";
                case "css" -> "text/css";
                case "js" -> "application/javascript";
                case "json" -> "application/json";
                case "xml" -> "application/xml";
                case "zip" -> "application/zip";
                case "mp4" -> "video/mp4";
                case "mp3" -> "audio/mpeg";
                default -> "application/octet-stream";
            };

        } catch (Exception e) {
            // If all else fails, return generic binary content type
            return "application/octet-stream";
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }
}
