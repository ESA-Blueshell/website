package net.blueshell.api.file.api

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * The blob store with nothing under it, for a test that is about a rule rather than a volume.
 *
 * Beside the port it stands in for (architecture ADR-003 rule 5) rather than in a central mock
 * package. In test fixtures rather than main sources, unlike the doubles that have to be
 * reachable from the dev profile: nothing runs against this one, and a bean of it would quietly
 * displace the real store in the integration tests that exist to prove the bytes reach a disk.
 */
class InMemoryBlobStore(initial: Map<String, ByteArray> = emptyMap()) : BlobStore {

    private val blobs = ConcurrentHashMap<String, ByteArray>().apply { putAll(initial) }

    /** Every key that holds bytes, so a test can say where something was written. */
    val keys: Set<String> get() = blobs.keys.toSet()

    override fun exists(key: String): Boolean = blobs.containsKey(key)

    override fun sizeOf(key: String): Long? = blobs[key]?.size?.toLong()

    override fun open(key: String): InputStream =
        ByteArrayInputStream(blobs[key] ?: throw BlobNotStored(key))

    override fun put(key: String, content: InputStream): Long {
        val bytes = content.use { it.readBytes() }
        return blobs.putIfAbsent(key, bytes)?.size?.toLong() ?: bytes.size.toLong()
    }

    override fun delete(key: String) {
        blobs.remove(key)
    }
}
