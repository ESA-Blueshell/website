package net.blueshell.api.blog.web.mapper

import net.blueshell.api.blog.web.dto.BlogDTO
import net.blueshell.api.blog.persistence.Blog
import net.blueshell.api.shared.mapper.BaseMapper
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class BlogMapper : BaseMapper<Blog, BlogDTO>() {
    @Value($$"${frontend.url}")
    private lateinit var frontendUrl: String

    override fun fromDTO(dto: BlogDTO): Blog = fromDTO(dto, Blog())

    fun fromDTO(dto: BlogDTO, blog: Blog): Blog {
        blog.title = requireNotNull(dto.title)
        blog.publishedAt = dto.publishedAt
        dto.version?.let { blog.version = it }
        afterFromDTO(dto, blog)
        return blog
    }

    private fun afterFromDTO(dto: BlogDTO, blog: Blog) {
        val content = dto.html
        if (content != null && content.trim { it <= ' ' }.isNotEmpty()) {
            val doc = Jsoup.parse(content)
            doc.select("div:has(a:contains(Unsubscribe))").remove()
            val minifiedHtml = doc.html().replace(">\\s+<".toRegex(), "><").trim { it <= ' ' }
            blog.html = minifiedHtml
        }
    }

    override fun toDTO(blog: Blog): BlogDTO {
        return BlogDTO(
            title = blog.title,
            html = blog.html,
            publishedAt = blog.publishedAt
        ).also { dto ->
            dto.id = blog.id
            dto.version = blog.version
            afterToDTO(blog, dto)
        }
    }

    private fun afterToDTO(blog: Blog, dto: BlogDTO) {
        dto.url = "$frontendUrl/blogs/${blog.id}"
    }

    /**
     * Extracts the title using the "h1.default-heading1" selector.
     * Uses the parsed Document to avoid reparsing.
     */
    private fun extractTitle(doc: Document): String {
        val titleElement = doc.selectFirst("h1.default-heading1")
        return if (titleElement != null) titleElement.text().trim { it <= ' ' } else ""
    }

    private fun extractImages(doc: Document): MutableList<net.blueshell.api.file.web.dto.FileDTO> {
        val files: MutableList<net.blueshell.api.file.web.dto.FileDTO> = ArrayList()
        for (img in doc.select("img")) {
            val src = img.attr("src")
            if (src.isEmpty()) {
                continue
            }
            val fileDTO = net.blueshell.api.file.web.dto.FileDTO()
            files.add(fileDTO)
        }
        return files
    }
}

fun Blog.asDTO(mapper: BlogMapper): BlogDTO = mapper.toDTO(this)

fun BlogDTO.asEntity(mapper: BlogMapper): Blog = mapper.fromDTO(this)
