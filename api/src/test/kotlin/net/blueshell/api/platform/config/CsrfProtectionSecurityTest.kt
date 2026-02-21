package net.blueshell.api.platform.config

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
class CsrfProtectionSecurityTest : UserTestSupport() {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var springSecurityFilterChain: FilterChainProxy

    private lateinit var rawMvc: MockMvc

    @BeforeEach
    fun setUpRawMockMvc() {
        val builder = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        builder.addFilters<DefaultMockMvcBuilder>(springSecurityFilterChain)
        rawMvc = builder.build()
    }

    @Test
    fun `csrf endpoint is public and returns csrf token`() {
        rawMvc.perform(get("/csrf"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
    }

    @Test
    fun `state changing request is rejected when csrf token is missing`() {
        val user = createUserWithRole(Role.MEMBER)

        rawMvc.perform(
            post("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"${user.username}","password":"Password123!"}""")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `state changing request is rejected when csrf token is invalid`() {
        val user = createUserWithRole(Role.MEMBER)

        rawMvc.perform(
            post("/auth")
                .with(csrf().asHeader().useInvalidToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"${user.username}","password":"Password123!"}""")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `state changing request succeeds when csrf token matches cookie`() {
        val user = createUserWithRole(Role.MEMBER)

        rawMvc.perform(
            post("/auth")
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"${user.username}","password":"Password123!"}""")
        )
            .andExpect(status().isOk)
    }
}
