package net.blueshell.api.mapper

import net.blueshell.api.base.BaseMapper
import net.blueshell.api.dto.BlogDTO
import net.blueshell.api.dto.FileDTO
import net.blueshell.api.model.Blog
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mapstruct.*
import org.springframework.beans.factory.annotation.Value

@Mapper(componentModel = "spring")
abstract class BlogMapper : BaseMapper<Blog?, BlogDTO?>() {
    @Value("\${frontend.url}")
    private val frontendUrl: String? = null


    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "title")
    @Mapping(target = "publishedAt")
    @Mapping(target = "version")
    abstract fun fromDTO(dto: BlogDTO?, @MappingTarget blog: Blog?): Blog?

    @AfterMapping
    protected fun afterFromDTO(dto: BlogDTO, @MappingTarget blog: Blog) {
        val content = dto.getHtml()
        if (content != null && !content.trim { it <= ' ' }.isEmpty()) {
            val doc = Jsoup.parse(content)
            doc.select("div:has(a:contains(Unsubscribe))").remove()
            val minifiedHtml = doc.html().replace(">\\s+<".toRegex(), "><").trim { it <= ' ' }
            blog.setHtml(minifiedHtml)
        }
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "title")
    @Mapping(target = "html")
    @Mapping(target = "publishedAt")
    @Mapping(target = "version")
    abstract override fun toDTO(blog: Blog?): BlogDTO?

    @AfterMapping
    protected fun afterToDTO(dto: BlogDTO, @MappingTarget blog: Blog) {
        dto.setUrl("%s/blogs/%s".formatted(frontendUrl, blog.getId()))
    }

    /**
     * Extracts the title using the "h1.default-heading1" selector.
     * Uses the parsed Document to avoid re-parsing.
     */
    private fun extractTitle(doc: Document): String {
        val titleElement = doc.selectFirst("h1.default-heading1")
        return if (titleElement != null) titleElement.text().trim { it <= ' ' } else ""
    }

    private fun extractImages(doc: Document): MutableList<FileDTO?> {
        val files: MutableList<FileDTO?> = ArrayList<FileDTO?>()
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
