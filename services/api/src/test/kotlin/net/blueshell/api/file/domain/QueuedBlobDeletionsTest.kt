package net.blueshell.api.file.domain

import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.persistence.BlobToDelete
import net.blueshell.api.file.persistence.BlobToDeleteRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.io.InputStream

class QueuedBlobDeletionsTest {
    /** Holds bytes by key, and refuses to delete the keys it is told to. */
    private class Store(
        vararg keys: String,
        private val refused: Set<String> = emptySet(),
    ) : BlobStore {
        val keys = keys.toMutableSet()

        override fun exists(key: String) = key in keys

        override fun sizeOf(key: String): Long? = if (key in keys) 1 else null

        override fun open(key: String): InputStream = InputStream.nullInputStream()

        override fun put(
            key: String,
            content: InputStream,
        ): Long = 1L.also { keys += key }

        override fun delete(key: String) {
            check(key !in refused) { "volume is read-only" }
            keys -= key
        }
    }

    @Test
    fun `the queued bytes are deleted and leave the queue, and the rest of the store stays`() {
        val signature = BlobToDelete("signatures/a.png")
        val queue = mock<BlobToDeleteRepository> { on { findAll() } doReturn listOf(signature) }
        val store = Store("signatures/a.png", "event-banners/b.webp")

        QueuedBlobDeletions(queue, store).onReady()

        assertThat(store.keys).containsExactly("event-banners/b.webp")
        verify(queue).delete(signature)
    }

    @Test
    fun `a path the store refuses stays queued for the next start, and the others still go`() {
        val refused = BlobToDelete("signatures/stuck.png")
        val gone = BlobToDelete("signatures/a.png")
        val queue = mock<BlobToDeleteRepository> { on { findAll() } doReturn listOf(refused, gone) }
        val store = Store("signatures/stuck.png", "signatures/a.png", refused = setOf("signatures/stuck.png"))

        QueuedBlobDeletions(queue, store).onReady()

        assertThat(store.keys).containsExactly("signatures/stuck.png")
        verify(queue).delete(gone)
        verify(queue, never()).delete(refused)
    }

    @Test
    fun `a queue that cannot be read leaves the start alone and deletes nothing`() {
        val queue = mock<BlobToDeleteRepository> { on { findAll() } doThrow IllegalStateException("no such table") }
        val store = mock<BlobStore>()

        assertThatCode { QueuedBlobDeletions(queue, store).onReady() }.doesNotThrowAnyException()

        verifyNoInteractions(store)
        verify(queue, never()).delete(any<BlobToDelete>())
    }

    @Test
    fun `an empty queue touches nothing`() {
        val queue = mock<BlobToDeleteRepository> { on { findAll() } doReturn emptyList() }
        val store = mock<BlobStore>()

        QueuedBlobDeletions(queue, store).onReady()

        verifyNoInteractions(store)
    }
}
