package net.blueshell.api.dto.response;

public record UploadFileResponse(String fileName, String fileDownloadUri, String fileType, long size) {
}
