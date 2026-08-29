package net.blueshell.api.file.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * The three containers cwebp writes, read back.
 *
 * Every fixture here is real encoder output rather than a hand-assembled header, and every one
 * is 40 by 25 so a transposed width and height cannot pass. The parser decides whether an
 * upload is already within its kind's ceiling, so a size read wrong here stores an unconverted
 * picture at whatever size it happened to arrive.
 */
class WebpDimensionsTest {

    /** `cwebp -q 82` on an opaque picture: a bare lossy frame. */
    private val lossy = decode(
        "UklGRqIBAABXRUJQVlA4IJYBAACQCwCdASooABkAPmEok0YkIiGhKrgKAIAMCWwAnTKEcDeQ" +
        "fhv7AlE/kv4A/WvKQOHPlA3ivaM/Wq5yXgAPKT9kvyQGYBCqoqMICF83qRdkfH1m4gGK/+8z" +
        "513PVRYya8iLIbWYAAD+/lng+epk+jqYfW5yui0ybgOwkQpFPwPTv+u0JLT3BO2xRazKKtpe" +
        "YBMJw333up4AUk80gjyZDJNqq6KXcTeYf2E1wzFc3CERST+TIgJhOG+lwOMOds2vfecM4Fpz" +
        "yzjuPy9L/6NRP4HoZfinkgvZRi7R9PW5tBl3CbgDT8pDLSu5248ef/ebY9T4FBfG+CksDZwq" +
        "yas27R/+f+YjLV9Hf4MEL+f+SsMqaUWWjIk+0LxZsrX/77V0uHf/G7m5kC9j/fvIqhNDYlWV" +
        "VUo3rSkNoZv3Q87RFjLnt/G+YbHWaRW1cJ66u4iEcOSH+YI8KdEJ5J4+az9iiqruVrq+vOrp" +
        "r5pIVN52mx9JtO8Nt1LudbZn4vBUI7N/8uT05rUb/oJRew7g0FU402K+iY8BqAAA",
    )

    /** `cwebp -lossless`: a VP8L frame, which spells its size in 14-bit fields. */
    private val lossless = decode("UklGRioAAABXRUJQVlA4TB4AAAAvJwAGALmM6H/sIqL/ASEBoft/WlXmYBpiAoCrui8=")

    /** Alpha forces the extended container, whose canvas size sits in a VP8X chunk. */
    private val extended = decode(
        "UklGRuQBAABXRUJQVlA4WAoAAAAQAAAAJwAAGAAAQUxQSCcAAAABD/DA/4iIoCaSpGjuji9E" +
            "AlKQhvdJERDR/3EIgFTT0tbR1RNfBgEAVlA4IJYBAACQCwCdASooABkAPmEok0YkIiGhKr" +
            "gKAIAMCWwAnTKEcDeQfhv7AlE/kv4A/WvKQOHPlA3ivaM/Wq5yXgAPKT9kvyQGYBCqoqMI" +
            "CF83qRdkfH1m4gGK/+8z513PVRYya8iLIbWYAAD+/lng+epk+jqYfW5yui0ybgOwkQpFPw" +
            "PTv+u0JLT3BO2xRazKKtpeX+POurEnijxt/Hp1fsloeMH8l1MnAzHuEsJrhmK5uEIikn8m" +
            "RATCcN9LgcYc7Zte+84ZwLTnlnHcfl6X/0aifwPQy/FPJBeyjF2j6etzaDLuE3AGn5SGWl" +
            "dztx48/+82x6nwKC+N8FJYGzhVk1Zt2j/8/8xGWr6O/wYIX8/8lYZU0ostGRJ9oXizZWv/" +
            "32rpcO/+N3NzIF7H+/eRVCaGxKsqqpRvWlIbQzfuh52iLGXPb+N8w2Os0itq4T11dxEI4c" +
            "kP8wR4U6ITyTx81n7FFVXcrXV9edXTXzSQqbztNj6Tad4bbqXc62zPxeCoR2b/5cnpzWo3" +
            "/QSi9h3BoKpxpsV9Ex4DUAAA",
    )

    @Test
    fun `reads a lossy frame`() {
        assertThat(WebpDimensions.of(lossy)).isEqualTo(ImageDimensions.Size(40, 25))
    }

    @Test
    fun `reads a lossless frame`() {
        assertThat(WebpDimensions.of(lossless)).isEqualTo(ImageDimensions.Size(40, 25))
    }

    @Test
    fun `reads the canvas of an extended container`() {
        assertThat(WebpDimensions.of(extended)).isEqualTo(ImageDimensions.Size(40, 25))
    }

    /** A PNG is not a WebP, and answering a size for one would store it without converting. */
    @Test
    fun `answers nothing for something that is not a WebP`() {
        assertThat(WebpDimensions.of(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())))
            .isNull()
    }

    /**
     * What makes the bounded read safe: the size fields sit in the first chunk's header, so a
     * prefix that stops long before the pixels is still enough to answer with.
     */
    @Test
    fun `answers the size from a prefix that stops before the pixels`() {
        assertThat(WebpDimensions.of(lossy.copyOf(32))).isEqualTo(ImageDimensions.Size(40, 25))
    }

    @Test
    fun `answers nothing when the prefix stops before the size fields`() {
        assertThat(WebpDimensions.of(lossy.copyOf(20))).isNull()
    }

    private fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)
}
