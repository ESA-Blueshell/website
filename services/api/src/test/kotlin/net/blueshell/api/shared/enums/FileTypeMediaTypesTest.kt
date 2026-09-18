package net.blueshell.api.shared.enums

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * What every kind of file admits, held to being a decision rather than an omission.
 *
 * `FileService` refuses a content type a kind does not list. It used to skip the check when the
 * list was empty, so four kinds that declared nothing accepted anything at all (#1220). These
 * assertions are what stops an empty list reading as "anything" again: a kind added without one
 * fails here rather than quietly admitting everything.
 */
class FileTypeMediaTypesTest {
    @ParameterizedTest
    @EnumSource(FileType::class)
    @DisplayName("a kind says what it admits")
    fun `a kind says what it admits`(type: FileType) {
        assertThat(type.allowedMediaTypes)
            .`as`("%s admits nothing, which refuses every upload of that kind", type)
            .isNotEmpty()
    }

    @ParameterizedTest
    @EnumSource(FileType::class)
    @DisplayName("a kind admits named types rather than a wildcard")
    fun `a kind admits named types rather than a wildcard`(type: FileType) {
        assertThat(type.allowedMediaTypes).allSatisfy { mediaType ->
            assertThat(mediaType)
                .`as`("%s admits %s, and a wildcard is the empty list by another spelling", type, mediaType)
                .doesNotContain("*")
                .isEqualTo(mediaType.lowercase())
        }
    }

    @ParameterizedTest
    @EnumSource(FileType::class)
    @DisplayName("only a kind that admits vectors admits an SVG")
    fun `only a kind that admits vectors admits an SVG`(type: FileType) {
        assertThat(VECTOR_MEDIA_TYPE in type.allowedMediaTypes)
            .`as`("%s and admitsVector disagree about whether an SVG may be stored as one", type)
            .isEqualTo(type.admitsVector)
    }

    @ParameterizedTest
    @EnumSource(value = FileType::class, names = ["PROFILE_PICTURE", "EVENT_PICTURE", "SPONSOR_PICTURE"])
    @DisplayName("a photograph kind takes photographs and not a vector")
    fun `a photograph kind takes photographs and not a vector`(type: FileType) {
        assertThat(type.allowedMediaTypes).contains("image/png", "image/jpeg", "image/webp")
        assertThat(type.allowedMediaTypes).doesNotContain(VECTOR_MEDIA_TYPE)
    }

    @ParameterizedTest
    @EnumSource(value = FileType::class, names = ["DOCUMENT"])
    @DisplayName("a document is a PDF")
    fun `a document is a PDF`(type: FileType) {
        assertThat(type.allowedMediaTypes).containsExactly("application/pdf")
    }
}
