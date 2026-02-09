package net.blueshell.api.blog.web.mapper

import net.blueshell.api.blog.web.dto.BlogDTO
import net.blueshell.api.blog.persistence.Blog
import net.blueshell.api.file.web.dto.FileDTO
import net.blueshell.api.shared.mapper.BaseMapper
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mapstruct.*
import org.springframework.beans.factory.annotation.Value

@Mapper(componentModel = "spring")
abstract class BlogMapper : BaseMapper<Blog, BlogDTO>() {
    @Value($$"${frontend.url}")
    private lateinit var frontendUrl: String

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "title")
    @Mapping(target = "publishedAt")
    @Mapping(target = "version")
    abstract fun fromDTO(dto: BlogDTO, @MappingTarget blog: Blog): Blog

    @AfterMapping
    protected fun afterFromDTO(dto: BlogDTO, @MappingTarget blog: Blog) {
        val content = dto.html
        if (content != null && !content.trim { it <= ' ' }.isEmpty()) {
            val doc = Jsoup.parse(content)
            doc.select("div:has(a:contains(Unsubscribe))").remove()
            val minifiedHtml = doc.html().replace(">\\s+<".toRegex(), "><").trim { it <= ' ' }
            blog.html = minifiedHtml
        }
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "title")
    @Mapping(target = "html")
    @Mapping(target = "publishedAt")
    @Mapping(target = "version")
    abstract override fun toDTO(blog: Blog): BlogDTO

    @AfterMapping
    protected fun afterToDTO(blog: Blog, @MappingTarget dto: BlogDTO) {
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

    private fun extractImages(doc: Document): MutableList<FileDTO> {
        val files: MutableList<FileDTO> = ArrayList()
        for (img in doc.select("img")) {
            val src = img.attr("src")
            if (src.isEmpty()) {
                continue
            }
            val fileDTO = FileDTO()
            files.add(fileDTO)
        }
        return files
    }
}
