package net.blueshell.api.file.api

import java.io.InputStream

/**
 * Where the bytes of a stored file are kept.
 *
 * A key is the stored path a `File` row carries — `event-banners/<sha256>.webp` — and it is an
 * address in this store, not a place on a disk. Nothing here says which: the site keeps its
 * uploads on a mounted volume today, and the only reason a caller could tell is if this
 * interface let it.
 *
 * Addresses are content hashes, so the same bytes always land at the same key and the bytes at
 * a key never change. That is what makes [put] a write-if-absent rather than an overwrite, and
 * what lets a served file be cached forever.
 */
interface BlobStore {

    /** Whether [key] holds bytes. */
    fun exists(key: String): Boolean

    /** How many bytes [key] holds, or nothing where it holds none. */
    fun sizeOf(key: String): Long?

    /**
     * The bytes at [key], for the caller to close.
     *
     * @throws BlobNotStored where [key] holds nothing.
     */
    fun open(key: String): InputStream

    /**
     * Puts [content] at [key] unless bytes are there already, answering how many bytes [key]
     * holds afterwards. Losing the race to another writer is not a failure: a key is a hash of
     * its contents, so both writers were storing the same bytes. [content] is closed here.
     */
    fun put(key: String, content: InputStream): Long

    /** Forgets [key]. Deleting what is not stored is the answer, not a failure. */
    fun delete(key: String)
}

/** Asked for bytes the store does not hold. */
class BlobNotStored(key: String) : RuntimeException("No bytes are stored at $key")
