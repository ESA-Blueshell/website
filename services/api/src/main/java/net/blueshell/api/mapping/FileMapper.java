package net.blueshell.api.mapping;

import jakarta.ws.rs.BadRequestException;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.FileDTO;
import net.blueshell.api.model.File;
import net.blueshell.api.service.FileService;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class FileMapper extends BaseMapper<File, FileDTO> {

    @Autowired
    private FileService fileService;

    @Value("${app.url}")
    private String appUrl;

    public String getDownloadURI(Path filePath) {
        return UriComponentsBuilder.fromUriString(appUrl).path("/download/").path(filePath.toString()).toUriString().replace("http://", "https://");
    }

    @Mapping(target = "fileName", ignore = true)
    @Mapping(target = "base64Content", ignore = true)
    public abstract FileDTO toDTO(File file);

    @Mapping(target = "name", ignore = true)
    @Mapping(target = "url", ignore = true)
    public abstract File fromDTO(FileDTO dto);

    @AfterMapping
    protected void afterFromDTO(FileDTO dto, @MappingTarget File file) {
        if (dto.getBase64Content() == null) {
            return;
        }
        String fileName = dto.getFileType().toString().toLowerCase() + "/";

        String mediaType = dto.getMediaType();
        String content = dto.getBase64Content();
        String fileExtension;
        try {
            fileExtension = MimeTypes.getDefaultMimeTypes().forName(mediaType.toString()).getExtension();
        } catch (MimeTypeException e) {
            throw new BadRequestException("Mime type not supported");
        }

        if (dto.getFileName() != null) {
            fileName += dto.getFileName();
        } else {
            fileName += content.hashCode() + "." + fileExtension;
        }

        byte[] data = Base64.getDecoder().decode(content);
        Path filePath = fileService.storeFile(fileName, data);

        file.setUrl(getDownloadURI(filePath));
        file.setCreatedAt(Timestamp.from(Instant.now()));
        file.setUploader(getPrincipal());
        file.setName(fileName);
        try {
            file.setSize(Files.size(filePath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
