package net.blueshell.api.system.frontend.blog

import net.blueshell.api.factory.blog.persistence.BlogFactory
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.function.Predicate

@Tag("system")
class BlogPagesSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var blogFactory: BlogFactory

    @Test
    fun `blogs page lists blogs and opens selected blog`() {
        val marker = System.currentTimeMillis()
        val blog = blogFactory.create(
            title = "System Blog $marker",
            html = "<h1>System Blog Body $marker</h1><p>Blog content for system test.</p>"
        )
        val blogId = checkNotNull(blog.id) { "Expected seeded blog id" }

        withPage { page ->
            val listResponse = page.waitForResponse(
                Predicate { response ->
                    isBlogListResponse(response)
                }
            ) {
                page.navigate("$frontendUrl/blogs")
            }
            assertThat(listResponse.status()).isEqualTo(200)

            waitFor(
                onTimeoutMessage = { "Expected blog title '${blog.title}' to appear on blog list page" }
            ) {
                page.getByText(blog.title, com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).count() > 0
            }

            page.getByText(blog.title, com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).first().click()
            page.waitForURL("**/blogs/$blogId")

            waitFor(
                onTimeoutMessage = { "Expected blog detail iframe to be visible for blog=$blogId" }
            ) {
                page.locator("iframe[title='Blog content']").count() > 0
            }

            val srcDoc = page.locator("iframe[title='Blog content']").first().getAttribute("srcdoc") ?: ""
            assertThat(srcDoc).contains("System Blog Body $marker")
        }
    }

    @Test
    fun `blog detail page loads from deep link`() {
        val marker = System.currentTimeMillis()
        val blog = blogFactory.create(
            title = "Deep Link Blog $marker",
            html = "<h2>Deep Link Content $marker</h2>"
        )
        val blogId = checkNotNull(blog.id) { "Expected seeded blog id" }

        withPage { page ->
            val detailResponse = page.waitForResponse(
                Predicate { response ->
                    isBlogDetailResponse(response, blogId)
                }
            ) {
                page.navigate("$frontendUrl/blogs/$blogId")
            }
            assertThat(detailResponse.status()).isEqualTo(200)

            waitFor(
                onTimeoutMessage = { "Expected blog detail iframe to be visible for blog=$blogId" }
            ) {
                page.locator("iframe[title='Blog content']").count() > 0
            }
            val srcDoc = page.locator("iframe[title='Blog content']").first().getAttribute("srcdoc") ?: ""
            assertThat(srcDoc).contains("Deep Link Content $marker")
        }
    }

    @Test
    fun `blog detail page shows not found state for missing blog`() {
        val missingBlogId = 9_999_999_999L

        withPage { page ->
            val detailResponse = page.waitForResponse(
                Predicate { response ->
                    isBlogDetailResponse(response, missingBlogId)
                }
            ) {
                page.navigate("$frontendUrl/blogs/$missingBlogId")
            }
            assertThat(detailResponse.status()).isEqualTo(404)

            waitFor(
                onTimeoutMessage = { "Expected not-found state when requested blog does not exist" }
            ) {
                page.getByText("Blog not found.", com.microsoft.playwright.Page.GetByTextOptions().setExact(true))
                    .count() > 0
            }
            assertThat(page.locator("iframe[title='Blog content']").count()).isEqualTo(0)
        }
    }

    private fun isBlogListResponse(response: com.microsoft.playwright.Response): Boolean {
        if (response.request().method() != "GET") return false
        if (response.request().resourceType() == "document") return false
        return response.url().matches(Regex(""".*/(?:api/)?blogs(?:\?.*)?$"""))
    }

    private fun isBlogDetailResponse(response: com.microsoft.playwright.Response, blogId: Long): Boolean {
        if (response.request().method() != "GET") return false
        if (response.request().resourceType() == "document") return false
        return response.url().matches(Regex(""".*/(?:api/)?blogs/$blogId(?:\?.*)?$"""))
    }
}
