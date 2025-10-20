package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.FileDTO;
import net.blueshell.api.model.File;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;

@Mapper(componentModel = "spring")
public abstract class FileMapper extends BaseMapper<File, FileDTO> {
    /**
     * Build hashed filename from content hash + original name's extension.
     */
    public String buildHashedFilename(String sha256, String originalName) {
        String ext = getExtensionSafe(originalName);
        return ext.isBlank() ? sha256 : (sha256 + "." + ext.toLowerCase());
    }

    /**
     * Resolve media type using preferred -> probed(path) -> fallback.
     */
    public String resolveMediaType(String filename, Path path, String preferred) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        try {
            String probed = Files.probeContentType(path);
            return (probed != null) ? probed : detectContentType(filename, new UrlResource(path.toUri()));
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    /**
     * After a file has been stored, fill the entity and computed URL.
     */
    public void populateAfterStore(
            @MappingTarget File file,
            String name,
            Path fullPath,
            String path,
            String mediaType
    ) {
        file.setName(name);
        file.setMediaType(mediaType);
        file.setUploaderId(getPrincipal().getId());
        try {
            file.setSize(Files.size(fullPath));
        } catch (IOException e) {
            throw new RuntimeException("Could not read file size for: " + path, e);
        }
        file.setPath(String.valueOf(path));
    }

    /**
     * Public helper for assets endpoint (moved from service).
     */
    public String detectContentTypeForAsset(String filename, Resource resource) {
        try {
            String contentType = Files.probeContentType(Path.of(filename));
            if (contentType != null) return contentType;
            resource.getFile();
            contentType = Files.probeContentType(resource.getFile().toPath());
            if (contentType != null) return contentType;
            return extToMime(getExtensionFromName(filename));
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    @Mapping(target = "id")
    @Mapping(target = "name")
    @Mapping(target = "mediaType")
    @Mapping(target = "type")
    @Mapping(target = "size")
    @BeanMapping(ignoreByDefault = true)
    public abstract FileDTO toDTO(File file);

    private String detectContentType(String filename, Resource resource) {
        try {
            String contentType = Files.probeContentType(Path.of(filename));
            if (contentType != null) return contentType;
            resource.getFile();
            contentType = Files.probeContentType(resource.getFile().toPath());
            if (contentType != null) return contentType;
            return extToMime(getExtensionFromName(filename));
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    private String getExtensionSafe(String originalName) {
        if (originalName == null) return "";
        String name = Path.of(originalName).getFileName().toString();
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) return "";
        return name.substring(i + 1);
    }

    private String getExtensionFromName(String filename) {
        int i = filename.lastIndexOf('.');
        if (i < 0 || i == filename.length() - 1) return "";
        return filename.substring(i + 1).toLowerCase();
    }

    private String extToMime(String ext) {
        return switch (ext) {
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
    }
}
