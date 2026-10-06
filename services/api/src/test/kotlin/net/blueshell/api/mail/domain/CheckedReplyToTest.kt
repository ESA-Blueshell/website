package net.blueshell.api.mail.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CheckedReplyToTest {
    @Test
    fun `keeps one well-formed address, and nothing for a blank one`() {
        assertThat(checkedReplyTo(" events@esa-blueshell.nl ")).isEqualTo("events@esa-blueshell.nl")
        assertThat(checkedReplyTo("")).isNull()
        assertThat(checkedReplyTo(null)).isNull()
    }

    @Test
    fun `refuses what is not exactly one address, naming what was typed`() {
        for (wrong in listOf("board", "a@b.nl, c@d.nl", "Board <board@esa-blueshell.nl>", "board@")) {
            assertThatThrownBy { checkedReplyTo(wrong) }
                .isInstanceOfSatisfying(ReplyToNotAnAddress::class.java) { assertThat(it.facts["replyTo"]).isEqualTo(wrong) }
        }
    }
}
