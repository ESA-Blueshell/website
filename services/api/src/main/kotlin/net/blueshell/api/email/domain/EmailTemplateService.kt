package net.blueshell.api.email.domain

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

@Service
class EmailTemplateService(
    private val templateEngine: TemplateEngine,
) {
    @Value($$"${frontend.url}")
    private lateinit var frontendUrl: String

    private fun processTemplate(
        templateName: String,
        variables: MutableMap<String, Any>,
    ): String {
        val context = Context()
        context.setVariables(variables)
        return templateEngine.process(templateName, context)
    }

    /** The email template filled in, with [markdownContent] rendered into its body. */
    fun createEmail(
        recipientEmail: String,
        recipientName: String,
        mainTitle: String,
        markdownContent: String,
    ): String {
        val variables: MutableMap<String, Any> = HashMap()
        variables["frontendUrl"] = frontendUrl
        variables["emailContent"] = EmailBodyMarkdown.render(markdownContent)
        variables["sentTo"] = recipientEmail
        variables["fullName"] = recipientName
        variables["mainTitle"] = mainTitle

        return processTemplate("emails/email-template", variables)
    }
}
