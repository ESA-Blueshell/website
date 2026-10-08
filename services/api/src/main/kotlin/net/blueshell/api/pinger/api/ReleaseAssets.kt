package net.blueshell.api.pinger.api

/** An asset on a published release: the file name it was uploaded under and where it is served. */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
)

/** The assets attached to the project's latest published release. */
interface ReleaseAssets {
    /** The latest release's assets, or empty when the release cannot be read. */
    fun latest(): List<ReleaseAsset>
}
