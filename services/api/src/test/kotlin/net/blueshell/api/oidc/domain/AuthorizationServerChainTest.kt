package net.blueshell.api.oidc.domain

import net.blueshell.api.security.JwtAuthFilter
import net.blueshell.api.testsupport.StandInHttpSecurity
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.security.web.context.SecurityContextRepository

class AuthorizationServerChainTest {
    @Test
    fun `authorizations are gated once the sign-in is read`() {
        val http = StandInHttpSecurity.create()
        val repository = mock<SecurityContextRepository>()

        AuthorizationServerConfig().authorizationServerFilterChain(http, mock(), mock(), mock(), repository, mock())

        verify(http).addFilterAfter(any<DownstreamClientAuthorizationFilter>(), eq(JwtAuthFilter::class.java))
    }
}
