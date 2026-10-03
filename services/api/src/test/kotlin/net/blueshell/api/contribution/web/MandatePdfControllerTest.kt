package net.blueshell.api.contribution.web

import net.blueshell.api.contribution.domain.MandatePdf
import net.blueshell.api.contribution.domain.MandatePdfs
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.web.server.ResponseStatusException

class MandatePdfControllerTest {
    private val pdfs: MandatePdfs = mock()
    private val currentUser: CurrentUserProvider = mock()
    private val controller = MandatePdfController(pdfs, currentUser)

    @Test
    fun `answers the mandate as a PDF attachment kept nowhere, for who asked`() {
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(id = 9, roles = emptySet(), addressId = null))
        whenever(pdfs.pdf(12, 9)).thenReturn(MandatePdf("mandate-BLUESHELL-12-20260930.pdf", byteArrayOf(1, 2)))

        val answer = controller.downloadMandatePdf(12)

        assertThat(answer.body).containsExactly(1, 2)
        assertThat(answer.headers.contentType).isEqualTo(MediaType.APPLICATION_PDF)
        assertThat(answer.headers.getFirst("Content-Disposition")).contains("mandate-BLUESHELL-12-20260930.pdf")
        assertThat(answer.headers.cacheControl).isEqualTo("no-store")
    }

    @Test
    fun `refuses somebody not signed in`() {
        whenever(currentUser.currentUser()).thenReturn(null)

        assertThatThrownBy { controller.downloadMandatePdf(12) }.isInstanceOf(ResponseStatusException::class.java)
    }
}
