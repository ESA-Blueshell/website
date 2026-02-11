package net.blueshell.api.domain.blog.web

import net.blueshell.api.domain.blog.web.dto.BlogDTO
import net.blueshell.api.factory.UnifiedFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.domain.user.persistence.User
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.temporal.ChronoUnit
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
class BlogControllerIT @Autowired constructor(
    private val uf: UnifiedFactory
) : UserTestSupport() {

    private val users = EnumMap<Role, User>(Role::class.java)

    @BeforeEach
    fun setup() {
        users[Role.BOARD] = createUserWithRole(Role.BOARD)
    }

    private fun givenBlogCreatedByBoard(): BlogDTO {
        val payload = uf.full(BlogDTO::class.java)
        val result = mvc.perform(
            post("/blogs")
                .with(bearer(users[Role.BOARD]!!))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(payload))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andReturn()

        return mapper.readValue(result.response.contentAsByteArray, BlogDTO::class.java)
    }

    @Test
    fun `posts are created correctly`() {
        val payload = uf.full(BlogDTO::class.java)

        mvc.perform(
            post("/blogs")
                .with(bearer(users[Role.BOARD]!!))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(payload))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(
                jsonPath("$.publishedAt")
                    .value(payload.publishedAt?.truncatedTo(ChronoUnit.SECONDS).toString())
            )
            .andExpect(jsonPath("$.title").value(payload.title))
    }

    @Test
    fun `fetching blogs works`() {
        givenBlogCreatedByBoard()

        mvc.perform(get("/blogs").with(bearer(users[Role.BOARD]!!)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize<Any>(1)))
    }

    @Test
    fun `fetching blogs by id works`() {
        val created = givenBlogCreatedByBoard()

        mvc.perform(get("/blogs/{id}", created.id).with(bearer(users[Role.BOARD]!!)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(created.id.toString()))
            .andExpect(jsonPath("$.title").value(created.title))
    }

    @Test
    fun `deleting blogs removes them`() {
        val created = givenBlogCreatedByBoard()

        mvc.perform(delete("/blogs/{id}", created.id).with(bearer(users[Role.BOARD]!!)))
            .andExpect(status().isNoContent())

        mvc.perform(get("/blogs").with(bearer(users[Role.BOARD]!!)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        mvc.perform(get("/blogs/{id}", created.id).with(bearer(users[Role.BOARD]!!)))
            .andExpect(status().isNotFound())
    }
}
