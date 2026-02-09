package net.blueshell.api.platform.integration.email.service

import net.blueshell.api.user.domain.model.User
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

@Service
class EmailTemplateService(templateEngine: TemplateEngine) {
    private val parser: Parser
    private val renderer: HtmlRenderer
    private val templateEngine: TemplateEngine

    @Value($$"${frontend.url}")
    private lateinit var appUrl: String

    init {
        val extensions = listOf(TablesExtension.create())
        this.parser = Parser.builder().extensions(extensions).build()
        this.renderer = HtmlRenderer.builder().extensions(extensions).build()
        this.templateEngine = templateEngine
    }

    private fun processTemplate(templateName: String, variables: MutableMap<String, Any>): String {
        val context = Context()
        context.setVariables(variables)
        return templateEngine.process(templateName, context)
    }

    /**
     * Send an email using the template with Markdown content
     * 
     * @param toUser          The recipient
     * @param mainTitle       The main title for the email
     * @param markdownContent The email content in Markdown format
     * @return The processed HTML email content
     */
    fun createEmail(
        toUser: User,
        mainTitle: String,
        markdownContent: String
    ): String {
        // Convert Markdown to HTML
        val document = parser.parse(markdownContent)
        val htmlContent = renderer.render(document)

        // Prepare template variables
        val variables: MutableMap<String, Any> = HashMap()
        variables["appUrl"] = appUrl
        variables["emailContent"] = htmlContent
        variables["sentTo"] = toUser.email
        variables["fullName"] = toUser.fullName
        variables["mainTitle"] = mainTitle

        // Process the template
        return processTemplate("emails/email-template", variables)
    }
}