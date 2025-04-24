package net.blueshell.fileservice.mapper;

import jakarta.ws.rs.BadRequestException;
import net.blueshell.dto.FileDTO;
import net.blueshell.mapper.BaseMapper;
import net.blueshell.fileservice.service.FileService;
import net.blueshell.fileservice.model.File;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;

/**
 * Mapper that converts between FileURLDTO <-> File entity.
 */
@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class FileMapper extends BaseMapper<File, FileDTO> {

    @Autowired
    private FileService fileService;

    @Value("${app.url}")
    private String appUrl;

    /** Constructs the final download URL that will serve the stored file. */
    public String getDownloadURI(Path filePath) {
        return UriComponentsBuilder
                .fromUriString(appUrl)
                .path("/files/")
                .path(filePath.toString())
                .toUriString()
                .replace("http://", "https://");
    }

    /**
     * Basic mapping from DTO to File without considering content/url specifics;
     * we’ll fill those in after mapping (see `afterFromDTO`).
     */
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "url", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "mediaType", ignore = true)
    @Mapping(target = "size", ignore = true)
    @Mapping(target = "fileType", ignore = true)
    public abstract File fromDTO(FileDTO dto);

    /**
     * Handles the logic of extracting either base64 content or remote URL content,
     * storing the file, and populating additional fields on the `File` entity.
     */
    @AfterMapping
    protected void afterFromDTO(FileDTO dto, @MappingTarget File file) {
        // If we have base64 content, handle it:
        if (dto.getBase64Content() != null) {
            processBase64Content(dto, file);
            return;
        }

        // Otherwise, if there is a remote URL to download, handle that:
        if (dto.getUrl() != null && !dto.getUrl().isBlank()) {
            processRemoteURLContent(dto, file);
        }
    }

    /**
     * Convert the final `File` entity back to a DTO.
     * (If you want FileURLDTO returned, you can similarly fill in relevant fields.)
     */
    @InheritInverseConfiguration
    @Mapping(target = "base64Content", ignore = true) // Not needed when returning to client
    @Mapping(target = "fileName", ignore = true)
    public abstract FileDTO toDTO(File file);

    /**
     * Private helper to handle base64 scenario.
     */
    private void processBase64Content(FileDTO dto, File file) {
        // Make sure mediaType is not null
        if (dto.getMediaType() == null) {
            throw new BadRequestException("MediaType is required when using base64 content");
        }

        String fileExtension = getFileExtension(dto.getMediaType());
        // Construct file name
        String fileName = computeFileName(dto, fileExtension);

        // Decode data and store
        byte[] data = Base64.getDecoder().decode(dto.getBase64Content());
        Path filePath = fileService.storeFile(fileName, data);

        // Populate entity
        populateFileEntity(file, fileName, filePath, dto.getMediaType());
    }

    /**
     * Private helper to handle remote URL scenario.
     */
    private void processRemoteURLContent(FileDTO dto, File file) {
        // If no mediaType is provided, try to guess or force a default
        String mediaType = dto.getMediaType() != null
                ? dto.getMediaType()
                : "application/octet-stream";

        // Compute file name / extension (can be improved with more logic)
        String fileExtension = getFileExtension(mediaType);
        String fileName = computeFileName(dto, fileExtension);

        // Download the bytes from the remote URL
        byte[] data;
        try (InputStream is = new URI(dto.getUrl()).toURL().openStream()) {
            data = is.readAllBytes();
        } catch (IOException | URISyntaxException e) {
            throw new BadRequestException("Failed to read from remote URL: " + dto.getUrl());
        }

        // Store the file
        Path filePath = fileService.storeFile(fileName, data);
        // Populate entity fields
        populateFileEntity(file, fileName, filePath, mediaType);
    }

    /**
     * Use Apache Tika to get the correct extension for a media type.
     */
    private String getFileExtension(String mediaType) {
        try {
            return MimeTypes.getDefaultMimeTypes()
                    .forName(mediaType)
                    .getExtension();
        } catch (MimeTypeException e) {
            throw new BadRequestException("Mime type not supported: " + mediaType);
        }
    }

    /**
     * Logic to determine final stored file name.
     */
    private String computeFileName(FileDTO dto, String fileExtension) {
        // If FileType is part of your DTO, you can prefix it. Otherwise adapt as you see fit.
        // e.g.: String fileName = dto.getFileType().toString().toLowerCase() + "/";

        String prefix = dto.getFileType() != null
                ? dto.getFileType().toString().toLowerCase() + "/"
                : "";

        if (dto.getFileName() != null) {
            return prefix + dto.getFileName();
        } else {
            // fallback if no file name is given
            return prefix + (dto.getBase64Content().hashCode() + fileExtension);
        }
    }

    /**
     * Populates the rest of the File entity after we have a stored file path.
     */
    private void populateFileEntity(File file, String fileName, Path filePath, String mediaType) {
        file.setName(fileName);
        file.setUrl(getDownloadURI(filePath));
        file.setCreatedAt(Timestamp.from(Instant.now()));
        file.setMediaType(mediaType);

        try {
            file.setSize(Files.size(filePath));
        } catch (IOException e) {
            throw new RuntimeException("Could not read file size of stored file: " + filePath, e);
        }
    }
}
