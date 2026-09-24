package net.blueshell.api.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository

class SecurityContextRepositoryConfigTest {
    @Test
    fun `a security context lives only as long as its request`() {
        assertThat(
            SecurityContextRepositoryConfig().securityContextRepository(),
        ).isInstanceOf(RequestAttributeSecurityContextRepository::class.java)
    }
}
