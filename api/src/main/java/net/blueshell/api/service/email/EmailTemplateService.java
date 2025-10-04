package net.blueshell.api.service.email;

import net.blueshell.api.model.User;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmailTemplateService {

    private final Parser parser;
    private final HtmlRenderer renderer;
    @Value("${frontend.url}")
    private String appUrl;
    @Autowired
    private TemplateEngine templateEngine;

    public EmailTemplateService() {
        var extensions = List.of(TablesExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer = HtmlRenderer.builder().extensions(extensions).build();
    }

    private String processTemplate(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(templateName, context);
    }

    /**
     * Send an email using the template with markdown content
     *
     * @param toUser          The recipient
     * @param mainTitle       The main title for the email
     * @param markdownContent The email content in markdown format
     * @return The processed HTML email content
     */
    public String createEmail(
            User toUser,
            String mainTitle,
            String markdownContent
    ) {
        // Convert markdown to HTML
        Node document = parser.parse(markdownContent);
        String htmlContent = renderer.render(document);

        // Prepare template variables
        Map<String, Object> variables = new HashMap<>();
        variables.put("appUrl", appUrl);
        variables.put("emailContent", htmlContent);
        variables.put("sentTo", toUser.getEmail());
        variables.put("fullName", toUser.getFullName());
        variables.put("mainTitle", mainTitle);

        // Process the template
        return processTemplate("emails/email-template", variables);
    }
}