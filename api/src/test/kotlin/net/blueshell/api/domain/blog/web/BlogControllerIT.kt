package net.blueshell.api.domain.blog.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@SpringBootTest
class BlogControllerIT : UserTestSupport() {

    @Test
    fun `creates lists finds updates and deletes a blog`() {
        val board = createUserWithRole(Role.BOARD)
        val publishedAt = Instant.now().toString()

        val createResult = mvc.perform(
            post("/blogs")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Integration Blog","html":"<p>Body</p>","publishedAt":"$publishedAt"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andExpect(jsonPath("$.title").value("Integration Blog"))
            .andReturn()

        val created = mapper.readTree(createResult.response.contentAsByteArray)
        val id = created.path("id").asLong()
        val version = created.path("version").asLong()

        mvc.perform(get("/blogs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(id))

        mvc.perform(get("/blogs/{id}", id))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.title").value("Integration Blog"))

        mvc.perform(
            post("/blogs/{id}", id)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"title":"Updated Integration Blog","html":"<p>Updated</p>","publishedAt":"$publishedAt","version":$version}"""
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.title").value("Updated Integration Blog"))

        mvc.perform(delete("/blogs/{id}", id).with(bearer(board)))
            .andExpect(status().isNoContent)

        mvc.perform(get("/blogs/{id}", id))
            .andExpect(status().isNotFound)
    }
}
