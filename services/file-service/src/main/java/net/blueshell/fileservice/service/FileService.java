package net.blueshell.fileservice.service;

import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import net.blueshell.exception.FileNotFoundException;
import net.blueshell.exception.StorageException;
import net.blueshell.db.BaseModelService;
import net.blueshell.fileservice.config.StorageConfig;
import net.blueshell.fileservice.model.File;
import net.blueshell.fileservice.repository.FileRepository;
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
}
