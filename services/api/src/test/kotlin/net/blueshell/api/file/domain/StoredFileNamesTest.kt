package net.blueshell.api.file.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** What a stored file is called — a rule, so provable without a volume to store one on. */
class StoredFileNamesTest {

    @Nested
    inner class HashedName {

        @Test
        fun `the address is the hash and the uploaded extension`() {
            assertThat(StoredFileNames.hashedName(HASH, "holiday.png")).isEqualTo("$HASH.png")
        }

        @Test
        fun `an extension is lower-cased, so the same bytes are one address and not two`() {
            assertThat(StoredFileNames.hashedName(HASH, "HOLIDAY.PNG")).isEqualTo("$HASH.png")
        }

        @Test
        fun `a name with no extension is stored under the bare hash`() {
            assertThat(StoredFileNames.hashedName(HASH, "README")).isEqualTo(HASH)
        }

        @Test
        fun `only the last dot starts the extension`() {
            assertThat(StoredFileNames.hashedName(HASH, "notes.tar.gz")).isEqualTo("$HASH.gz")
        }

        @Test
        fun `the same bytes under two names share one address`() {
            assertThat(StoredFileNames.hashedName(HASH, "a.png"))
                .isEqualTo(StoredFileNames.hashedName(HASH, "b.png"))
        }
    }

    @Nested
    inner class ServedName {

        @Test
        fun `converted bytes are saved under the extension the bytes actually have`() {
            assertThat(StoredFileNames.servedName("holiday.jpg", "team-banners/$HASH.webp"))
                .isEqualTo("holiday.webp")
        }

        @Test
        fun `bytes that were not converted keep the uploaded name`() {
            assertThat(StoredFileNames.servedName("report.pdf", "documents/$HASH.pdf"))
                .isEqualTo("report.pdf")
        }

        @Test
        fun `the stored extension is matched whatever case it was written in`() {
            assertThat(StoredFileNames.servedName("holiday.JPG", "documents/$HASH.jpg"))
                .isEqualTo("holiday.JPG")
        }

        @Test
        fun `a name with no extension gains the stored one`() {
            assertThat(StoredFileNames.servedName("holiday", "team-banners/$HASH.webp"))
                .isEqualTo("holiday.webp")
        }

        @Test
        fun `a stored file with no extension leaves the uploaded name alone`() {
            assertThat(StoredFileNames.servedName("holiday.jpg", "documents/$HASH"))
                .isEqualTo("holiday.jpg")
        }
    }

    @Nested
    inner class ExtensionOf {

        @Test
        fun `a directory that contains a dot is not an extension`() {
            assertThat(StoredFileNames.extensionOf("my.photos/holiday")).isEmpty()
        }

        @Test
        fun `a name ending in a dot has no extension`() {
            assertThat(StoredFileNames.extensionOf("holiday.")).isEmpty()
        }

        @Test
        fun `a dotfile is all extension and no stem, which is what a browser may send`() {
            assertThat(StoredFileNames.extensionOf(".gitignore")).isEqualTo("gitignore")
        }
    }

    @Test
    fun `a key is the kind's directory and the file's name`() {
        assertThat(StoredFileNames.keyOf("event-banners", "$HASH.webp"))
            .isEqualTo("event-banners/$HASH.webp")
    }

    private companion object {
        const val HASH = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
    }
}
