package net.blueshell.api.email.domain

import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Code
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.AttributeProviderContext
import org.commonmark.renderer.html.AttributeProviderFactory
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Renders the body Markdown of an email to HTML that carries its own colours.
 *
 * Several clients, among them the Gmail app on a non-Gmail account, strip `<style>` and then
 * restyle any element without an explicit colour for their own dark theme, which turns the copy
 * dark on the dark canvas. Every element therefore gets the stylesheet's declarations inline.
 */
object EmailBodyMarkdown {
    private const val FONT = "'Barlow Semi Condensed','Segoe UI',Arial,sans-serif"
    private const val TEXT = "#FFFFFF"
    private const val MUTED = "#E7E7E7"
    private const val ACCENT = "#3387FA"
    private const val BORDER = "#3A3A3A"

    private val bodyStyle =
        "margin:0 0 16px 0; color:$TEXT; font-family:$FONT; font-size:18px; line-height:1.6;"

    private val styles: Map<String, String> =
        mapOf(
            "p" to bodyStyle,
            "ul" to "margin:0 0 16px 0; padding-left:24px; color:$TEXT; font-family:$FONT;",
            "ol" to "margin:0 0 16px 0; padding-left:24px; color:$TEXT; font-family:$FONT;",
            "li" to "margin:6px 0; color:$TEXT; font-family:$FONT; font-size:18px; line-height:1.6;",
            "a" to "color:#FBFAFA; text-decoration:underline;",
            "strong" to "color:$TEXT; font-weight:700;",
            "em" to "color:$MUTED;",
            "blockquote" to
                "border-left:3px solid $ACCENT; padding:4px 18px; margin:0 0 16px 0; " +
                "color:$MUTED; font-family:$FONT; font-size:18px; font-style:italic;",
            "code" to
                "font-family:'Courier New',Courier,monospace; background-color:#2A2A2A; " +
                "color:#7FE0F2; padding:2px 6px; border-radius:4px; font-size:15px;",
            "pre" to
                "background-color:#161616; border-left:3px solid $ACCENT; padding:14px 16px; " +
                "border-radius:6px; margin:0 0 16px 0; white-space:pre-wrap; word-wrap:break-word;",
            "hr" to
                "border:0; border-top:1px solid $BORDER; height:1px; " +
                "background-color:$BORDER; color:$BORDER; margin:24px 0;",
            "table" to
                "width:100%; border-collapse:collapse; margin:0 0 16px 0; " +
                "color:$TEXT; font-family:$FONT; font-size:16px;",
            "th" to
                "background-color:#2A2A2A; color:$TEXT; font-weight:600; text-align:left; " +
                "padding:10px 12px; border:1px solid $BORDER;",
            "td" to "color:$TEXT; padding:8px 12px; border:1px solid $BORDER; vertical-align:top;",
        )

    private val headingStyles: Map<Int, String> =
        mapOf(
            1 to "font-family:$FONT; font-size:30px; font-weight:700; line-height:1.15; " +
                "margin:0 0 14px 0; color:$ACCENT; letter-spacing:0.3px;",
            2 to "font-family:$FONT; font-size:22px; font-weight:700; line-height:1.25; " +
                "margin:26px 0 10px 0; color:$TEXT;",
            3 to "font-family:$FONT; font-size:18px; font-weight:600; line-height:1.3; " +
                "margin:20px 0 8px 0; color:$TEXT;",
        )

    private val smallHeadingStyle =
        "font-family:$FONT; font-size:16px; font-weight:600; line-height:1.3; " +
            "margin:16px 0 6px 0; color:$TEXT;"

    private val extensions = listOf(TablesExtension.create())
    private val parser: Parser = Parser.builder().extensions(extensions).build()

    private val renderer: HtmlRenderer =
        HtmlRenderer
            .builder()
            .extensions(extensions)
            .attributeProviderFactory(
                AttributeProviderFactory { _: AttributeProviderContext -> InlineStyles() },
            ).build()

    fun render(markdown: String): String = renderer.render(parser.parse(markdown))

    private fun styleFor(
        node: Node,
        tagName: String,
    ): String? =
        when {
            node is Heading -> headingStyles[node.level] ?: smallHeadingStyle
            // A code block nests <code> in <pre>; the nested copy keeps the block's own background.
            tagName == "code" && node !is Code ->
                "font-family:'Courier New',Courier,monospace; background-color:transparent; " +
                    "color:$TEXT; padding:0; font-size:15px;"
            else -> styles[tagName]
        }

    private class InlineStyles : AttributeProvider {
        override fun setAttributes(
            node: Node,
            tagName: String,
            attributes: MutableMap<String, String>,
        ) {
            styleFor(node, tagName)?.let { attributes["style"] = it }
        }
    }
}
