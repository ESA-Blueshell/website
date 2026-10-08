package net.blueshell.api.pinger.api

import org.springframework.stereotype.Service

/** Resolves where a member fetches the desktop pinger installer for their platform. */
@Service
class PingerAppDownloads(
    private val releases: ReleaseAssets,
) {
    /**
     * The download URL for [platform]'s installer on the latest release, or null when that release
     * carries none for it.
     */
    fun installerUrl(platform: PingerPlatform): String? =
        releases
            .latest()
            .firstOrNull { it.name.equals(platform.assetName, ignoreCase = true) }
            ?.downloadUrl
}
