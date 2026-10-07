package net.blueshell.api.pinger

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * The one paint job the SNTPings pinger reads: the prefix it targets, the rate, the box the image
 * lands in on the 4K canvas and where that image is stored. An admin steers it from the main site;
 * the pinger service and its helper exe read the public view of it.
 */
@PackageInfo
@ApplicationModule(
    id = "pinger",
    allowedDependencies = [
        // Open kernel: the paint writes are @AdminOnly.
        "security",
        // Open kernel.
        "shared",
        // The uploaded image is resolved to a public file through StoredPictures and its URL.
        "file :: api",
        // StoredPictures hands back the File the stored path resolves to; we read its path.
        "file :: entities",
    ],
)
class ModuleMetadata
