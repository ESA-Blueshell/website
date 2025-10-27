package net.blueshell.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FileUpload")
public record UploadFileResponse(String fileName, String fileDownloadUri, String fileType, long size) {
}
