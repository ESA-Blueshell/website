package net.blueshell.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.common.enums.FileType;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = false)
public class FileDTO extends BaseDTO {
    private Long id;
    private String name;
    private String url;
    private Long uploaderId;
    private Timestamp createdAt;
    private String mediaType;
    private Long size;
    private String fileName;
    private FileType fileType;
    private String base64Content;
}