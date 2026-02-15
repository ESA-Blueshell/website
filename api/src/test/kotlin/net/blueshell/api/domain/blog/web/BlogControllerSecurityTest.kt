package net.blueshell.api.domain.blog.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for BlogController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - BOARD users can create/update/delete blogs
 * - Non-BOARD users cannot modify blogs
 * - Public read access to blog posts
 */
@SpringBootTest
class BlogControllerSecurityTest : UserTestSupport() {

    @Nested
    inner class CreateBlog {

        @Test
        fun `allows BOARD to create blogs`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/blogs")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"New Blog","html":"<p>Content</p>","publishedAt":"2026-01-01T12:00:00Z"}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from creating blogs`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/blogs")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"New Blog","html":"<p>Content</p>","publishedAt":"2026-01-01T12:00:00Z"}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                post("/blogs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"New Blog","html":"<p>Content</p>","publishedAt":"2026-01-01T12:00:00Z"}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class UpdateBlog {

        @Test
        fun `allows BOARD to update blogs`() {
            val board = createUserWithRole(Role.BOARD)
            val blogId = createBlogFixture().id!!

            mvc.perform(
                post("/blogs/{id}", blogId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Updated Blog","html":"<p>Updated Content</p>","publishedAt":"2026-01-01T12:00:00Z"}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from updating blogs`() {
            val member = createUserWithRole(Role.MEMBER)
            val blogId = createBlogFixture().id!!

            mvc.perform(
                post("/blogs/{id}", blogId)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Hacked Blog","html":"<p>Hacked</p>","publishedAt":"2026-01-01T12:00:00Z"}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val blogId = createBlogFixture().id!!

            mvc.perform(
                post("/blogs/{id}", blogId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Unauthorized","html":"<p>Unauthorized</p>","publishedAt":"2026-01-01T12:00:00Z"}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindBlogs {

        @Test
        fun `allows anyone to list blogs`() {
            mvc.perform(get("/blogs"))
                .andExpect(status().isOk)
        }

        @Test
        fun `allows authenticated user to list blogs`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/blogs")
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows unauthenticated access to blogs`() {
            mvc.perform(get("/blogs"))
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class FindBlogById {

        @Test
        fun `allows anyone to read blog details`() {
            val blogId = createBlogFixture().id!!

            mvc.perform(get("/blogs/{id}", blogId))
                .andExpect(status().isOk)
        }

        @Test
        fun `allows authenticated user to read blog details`() {
            val member = createUserWithRole(Role.MEMBER)
            val blogId = createBlogFixture().id!!

            mvc.perform(
                get("/blogs/{id}", blogId)
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows unauthenticated access to blog details`() {
            val blogId = createBlogFixture().id!!

            mvc.perform(get("/blogs/{id}", blogId))
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class DeleteBlog {

        @Test
        fun `allows BOARD to delete blogs`() {
            val board = createUserWithRole(Role.BOARD)
            val blogId = createBlogFixture().id!!

            mvc.perform(
                delete("/blogs/{id}", blogId)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies non-BOARD users from deleting blogs`() {
            val member = createUserWithRole(Role.MEMBER)
            val blogId = createBlogFixture().id!!

            mvc.perform(
                delete("/blogs/{id}", blogId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val blogId = createBlogFixture().id!!

            mvc.perform(delete("/blogs/{id}", blogId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                post("/blogs")
                    .with(bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"New Blog","html":"<p>Content</p>","publishedAt":"2026-01-01T12:00:00Z"}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `COMMITTEE cannot create blogs`() {
            val committee = createUserWithRole(Role.COMMITTEE)

            mvc.perform(
                post("/blogs")
                    .with(bearer(committee))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"New Blog","html":"<p>Content</p>","publishedAt":"2026-01-01T12:00:00Z"}""")
            )
                .andExpect(status().isForbidden)
        }
    }
}
