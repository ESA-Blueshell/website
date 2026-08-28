package net.blueshell.api.domain.file

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Uploaded files: storage, content-type and size validation on the way in, and the `File` rows
 * other modules point their logos, portraits and banners at.
 *
 * Declares `EventBannerFileLookup`, implemented by `event`, so resolving an event's banner does
 * not require this module to know what an event is.
 */
@PackageInfo
@ApplicationModule(
    id = "file",
    allowedDependencies = [
        // Open kernel.
        "shared",
        // The uploader is resolved through UserService.
        "user :: api",
        // File.uploader is an owning @OneToOne holding the FK into users.
        "user :: entities",
    ],
)
class ModuleMetadata
