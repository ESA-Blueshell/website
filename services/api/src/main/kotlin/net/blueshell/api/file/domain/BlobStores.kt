package net.blueshell.api.file.domain

import net.blueshell.api.file.api.BlobStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * The two stores this module reads, and the only place either root is named.
 *
 * Uploads and assets are the same kind of thing addressed the same way, and the split is which
 * store answers rather than a branch inside one: uploads are what visitors put there and are
 * kept on the mounted volume that survives a deploy, assets ship with the release and sit
 * beside the running application. Asking one for the other's key finds nothing, which is what
 * keeps an upload from being served as though the release shipped it.
 */
@Configuration(proxyBeanMethods = false)
class BlobStores {

    /** What was uploaded. The default store, since everything but an asset is one of these. */
    @Bean
    @Primary
    fun uploadBlobStore(@Value($$"${storage.location}") location: String): BlobStore =
        FilesystemBlobStore(location)

    /** What the release shipped, read-only in practice: nothing writes here. */
    @Bean
    fun assetBlobStore(): BlobStore = FilesystemBlobStore(ASSETS_LOCATION)

    private companion object {
        /** Relative, so it resolves beside the running application as it always has. */
        const val ASSETS_LOCATION = "assets"
    }
}
