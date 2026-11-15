package net.blueshell.api.file.api

import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

class SitePicturesTest {
    private val files = mock<FileService>()
    private val users = mock<UserService>()
    private val manager = mock<PlatformTransactionManager>()
    private val pictures = SitePictures(files, users, TransactionTemplate(manager))
    private val site = Entities.user(id = 1, username = "system")

    init {
        whenever(manager.getTransaction(any())).thenReturn(SimpleTransactionStatus())
        whenever(users.findByUsername("system")).thenReturn(site)
    }

    @Test
    fun `stores a fetched picture as the site's own, in a transaction of its own`() {
        val stored = File::class.java.getDeclaredConstructor().newInstance()
        whenever(files.store(any(), eq("cover.jpg"), eq("image/jpeg"), eq(FileType.VOTE_COVER), eq(site))).thenReturn(stored)

        assertThat(pictures.store(byteArrayOf(1), "cover.jpg", "image/jpeg", FileType.VOTE_COVER)).isSameAs(stored)
        verify(manager).getTransaction(
            org.mockito.kotlin.argThat<TransactionDefinition> {
                propagationBehavior ==
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW
            },
        )
    }

    @Test
    fun `answers nothing where the picture cannot be stored`() {
        whenever(files.store(any(), any(), any(), any(), any())).thenThrow(IllegalStateException("refused"))

        assertThat(pictures.store(byteArrayOf(1), "cover.jpg", "image/jpeg", FileType.VOTE_COVER)).isNull()
    }
}
