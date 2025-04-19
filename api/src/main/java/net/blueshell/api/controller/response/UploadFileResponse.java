package net.blueshell.api.controller.response;

public record UploadFileResponse(String fileName, String fileDownloadUri, String fileType, long size) {
}
