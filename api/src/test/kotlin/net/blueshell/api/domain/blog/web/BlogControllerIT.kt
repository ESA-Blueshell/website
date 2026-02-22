package net.blueshell.api.domain.blog.web

import net.blueshell.api.factory.blog.web.request.BlogRequestFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@SpringBootTest
class BlogControllerIT : UserTestSupport() {
    @Autowired
    private lateinit var blogRequestFactory: BlogRequestFactory

    @Nested
    inner class CreateBlog {
        @Test
        fun `creates blog`() {
            val board = createUserWithRole(Role.BOARD)
            val publishedAt = Instant.now().toString()

            mvc.perform(
                post("/blogs")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(blogRequestFactory.createPayload("Integration Blog", "<p>Body</p>", publishedAt))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNumber)
                .andExpect(jsonPath("$.title").value("Integration Blog"))
        }

        @Test
        fun `returns bad request for invalid payload`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/blogs")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"","html":"","publishedAt":null}""")
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `strips dangerous blog html during create`() {
            val board = createUserWithRole(Role.BOARD)
            val publishedAt = Instant.now().toString()

            mvc.perform(
                post("/blogs")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        blogRequestFactory.createPayload(
                            "Sanitized Blog",
                            "<p>Body</p><script>alert(1)</script><a href='javascript:alert(1)'>Click</a>",
                            publishedAt
                        )
                    )
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.html").value(containsString("<p>Body</p>")))
                .andExpect(jsonPath("$.html").value(not(containsString("<script"))))
                .andExpect(jsonPath("$.html").value(not(containsString("javascript:"))))
        }
    }

    @Nested
    inner class UpdateBlog {
        @Test
        fun `updates blog`() {
            val board = createUserWithRole(Role.BOARD)
            val blog = createBlogFixture(title = "Original Blog")
            val publishedAt = Instant.now().toString()

            mvc.perform(
                post("/blogs/{id}", blog.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        blogRequestFactory.updatePayload(
                            version = blog.version,
                            title = "Updated Blog",
                            html = "<p>Updated</p>",
                            publishedAt = publishedAt
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(blog.id))
                .andExpect(jsonPath("$.title").value("Updated Blog"))
        }

        @Test
        fun `returns not found when blog does not exist`() {
            val board = createUserWithRole(Role.BOARD)
            val publishedAt = Instant.now().toString()

            mvc.perform(
                post("/blogs/{id}", 999999L)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        blogRequestFactory.updatePayload(
                            version = 0,
                            title = "Updated Blog",
                            html = "<p>Updated</p>",
                            publishedAt = publishedAt
                        )
                    )
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class FindBlogs {
        @Test
        fun `lists blogs`() {
            val blog = createBlogFixture()

            mvc.perform(get("/blogs"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(blog.id))
        }
    }

    @Nested
    inner class FindBlogById {
        @Test
        fun `finds blog by id`() {
            val blog = createBlogFixture(title = "Lookup Blog")

            mvc.perform(get("/blogs/{id}", blog.id))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(blog.id))
                .andExpect(jsonPath("$.title").value("Lookup Blog"))
        }

        @Test
        fun `returns not found when blog does not exist`() {
            mvc.perform(get("/blogs/{id}", 999999L))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class DeleteById {
        @Test
        fun `deletes blog`() {
            val board = createUserWithRole(Role.BOARD)
            val blog = createBlogFixture()

            mvc.perform(
                delete("/blogs/{id}", blog.id)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)

            mvc.perform(get("/blogs/{id}", blog.id))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `returns not found when deleting missing blog`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                delete("/blogs/{id}", 999999L)
                    .with(bearer(board))
            )
                .andExpect(status().isNotFound)
        }
    }
}
