package net.blueshell.api.blog.web.dto

import net.blueshell.api.domain.blog.web.dto.SocialDTO
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SocialDtoIT : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `is not supported`() {
            val hasAsEntity = SocialDTO::class.java.methods.any { it.name == "asEntity" }

            assertThat(hasAsEntity).isFalse
        }
    }
}
