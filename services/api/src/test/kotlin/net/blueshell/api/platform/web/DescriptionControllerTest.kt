package net.blueshell.api.platform.web

import net.blueshell.api.shared.discord.DescriptionNodeKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DescriptionControllerTest {
    @Test
    fun `reads text as a description without saving it, and says how long it is as stored`() {
        val read = DescriptionController().previewDescription(DescriptionPreviewRequest("# LAN\n||soon||"))

        assertThat(read.nodes.map { it.kind }).containsExactly(DescriptionNodeKind.HEADING, DescriptionNodeKind.PARAGRAPH)
        assertThat(read.length).isEqualTo(14)
    }
}
