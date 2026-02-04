package net.blueshell.api.mapper

import net.blueshell.api.base.BaseMapper
import net.blueshell.api.dto.FileDTO
import net.blueshell.api.model.File
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

@Mapper(componentModel = "spring")
abstract class FileMapper : BaseMapper<File, FileDTO>() {
    /**
     * Build hashed filename from content hash + original name's extension.
     */
    fun buildHashedFilename(sha256: String, originalName: String): String {
        val ext = getExtensionSafe(originalName)
        return if (ext.isBlank()) sha256 else (sha256 + "." + ext.lowercase(Locale.getDefault()))
    }

    /**
     * Resolve media type using preferred -> probed(path) -> fallback.
     */
    fun resolveMediaType(filename: String, path: Path, preferred: String): String {
        if (preferred != null && !preferred.isBlank()) return preferred
        try {
            val probed = Files.probeContentType(path)
            return if (probed != null) probed else detectContentType(filename, UrlResource(path.toUri()))
        } catch (e: Exception) {
            return "application/octet-stream"
        }
    }

    /**
     * After a file has been stored, fill the entity and computed URL.
     */
    fun populateAfterStore(
        @MappingTarget file: File,
        name: String,
        fullPath: Path,
        path: String,
        mediaType: String
    ) {
        file.name = name
        file.mediaType = mediaType
        file.uploaderId = principal!!.id!!
        try {
            file.size = Files.size(fullPath)
        } catch (e: IOException) {
            throw RuntimeException("Could not read file size for: " + path, e)
        }
        file.path = path.toString()
    }

    /**
     * Public helper for assets endpoint (moved from service).
     */
    fun detectContentTypeForAsset(filename: String, resource: Resource): String {
        try {
            var contentType = Files.probeContentType(Path.of(filename))
            if (contentType != null) return contentType
            resource.file
            contentType = Files.probeContentType(resource.file.toPath())
            if (contentType != null) return contentType
            return extToMime(getExtensionFromName(filename))
        } catch (e: Exception) {
            return "application/octet-stream"
        }
    }

    @Mapping(target = "id")
    @Mapping(target = "name")
    @Mapping(target = "mediaType")
    @Mapping(target = "type")
    @Mapping(target = "size")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(file: File): FileDTO

    private fun detectContentType(filename: String, resource: Resource): String {
        try {
            var contentType = Files.probeContentType(Path.of(filename))
            if (contentType != null) return contentType
            resource.file
            contentType = Files.probeContentType(resource.file.toPath())
            if (contentType != null) return contentType
            return extToMime(getExtensionFromName(filename))
        } catch (e: Exception) {
            return "application/octet-stream"
        }
    }

    private fun getExtensionSafe(originalName: String): String {
        if (originalName == null) return ""
        val name = Path.of(originalName).fileName.toString()
        val i = name.lastIndexOf('.')
        if (i < 0 || i == name.length - 1) return ""
        return name.substring(i + 1)
    }

    private fun getExtensionFromName(filename: String): String {
        val i = filename.lastIndexOf('.')
        if (i < 0 || i == filename.length - 1) return ""
        return filename.substring(i + 1).lowercase(Locale.getDefault())
    }

    private fun extToMime(ext: String): String {
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "html" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "zip" -> "application/zip"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }
}
