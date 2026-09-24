package net.blueshell.api.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class BrowserTest {
    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0|Firefox|Windows",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36|Chrome|macOS",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36 Edg/129.0|Edge|Windows",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1|Safari|iOS",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36|Chrome|Android",
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0|Firefox|Linux",
            "curl/8.7.1|Unknown browser|unknown system",
        ],
    )
    fun `a user agent is read as a family and a system, never a version`(ua: String, family: String, platform: String) {
        assertThat(Browser.of(ua)).isEqualTo(Browser(family, platform))
    }

    @Test
    fun `an update does not change the browser`() {
        assertThat(Browser.of("Mozilla/5.0 (Windows NT 10.0; rv:131.0) Gecko/20100101 Firefox/131.0"))
            .isEqualTo(Browser.of("Mozilla/5.0 (Windows NT 10.0; rv:132.0) Gecko/20100101 Firefox/132.0"))
        assertThat(Browser.of(null)).isEqualTo(Browser.UNKNOWN)
    }
}
