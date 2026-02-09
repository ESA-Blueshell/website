package net.blueshell.api.blog.web.mapper

import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.factory.model.BlogFactory
import net.blueshell.api.blog.web.mapper.SocialMapper
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SocialMapperIT @Autowired constructor(
    private val socialMapper: SocialMapper,
    private val blogFactory: BlogFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `builds social dto from blog`() {
            val blog = persist(blogFactory.createBasic())
            val dto = socialMapper.toSocialDTO(blog)

            assertThat(dto.url).contains("/blogs")
            assertThat(dto.title).isEqualTo(blog.title)
            assertThat(dto.platforms).contains(
                PlatformType.FACEBOOK,
                PlatformType.TWITTER,
                PlatformType.INSTAGRAM,
                PlatformType.LINKEDIN
            )
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `is not supported`() {
            val hasFromDto = SocialMapper::class.java.methods.any { it.name == "fromDTO" }

            assertThat(hasFromDto).isFalse
        }
    }
}
