package net.blueshell.api.common.enums;

import lombok.Getter;

@Getter
public enum FileType {
    DOCUMENT("application/pdf", "pdf", "documents"),
    SIGNATURE("image/", null, "signatures"),
    PROFILE_PICTURE("image/", null, "profile-pictures"),
    EVENT_BANNER("image/", null, "event-banners"),
    EVENT_PICTURE("image/", null, "event-pictures"),
    SPONSOR_PICTURE("image/", null, "sponsor-pictures");

    private final String allowedContentType;
    private final String fileExtension;
    private final String directory;

    FileType(String allowedContentType, String fileExtension, String directory) {
        this.allowedContentType = allowedContentType;
        this.fileExtension = fileExtension;
        this.directory = directory;
    }

    public boolean isValidContentType(String contentType) {
        if (allowedContentType.endsWith("/")) {
            String baseType = allowedContentType.substring(0, allowedContentType.length() - 1);
            return contentType.startsWith(baseType);
        }
        return allowedContentType.equalsIgnoreCase(contentType);
    }

    public String generateFileName(String entityName, String fileExtension) {
        String sanitizedName = sanitize(entityName);
        String extension = this.fileExtension != null ? this.fileExtension : fileExtension;
        return String.format("%s/%s-%s.%s", directory, sanitizedName, name().toLowerCase(), extension);
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9\\s]", "").trim().replaceAll("\\s+", "-").toLowerCase();
    }
}
