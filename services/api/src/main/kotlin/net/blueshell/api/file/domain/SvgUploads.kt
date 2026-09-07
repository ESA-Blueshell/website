package net.blueshell.api.file.domain

import net.blueshell.api.shared.enums.VECTOR_MEDIA_TYPE
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.NodeList
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException

/**
 * What an uploaded vector is allowed to be.
 *
 * An SVG is a document rather than a bitmap: it can carry script, event handlers and references
 * to other origins, and it is served inline from the api's origin beside the api's cookies. So a
 * logo is held to what a logo needs — shapes, and references to itself — and anything else is
 * refused in words whoever chose the file can act on. Nothing is rewritten: the address of a
 * stored file is the hash of its contents, so storing bytes other than the ones handed over
 * would make that address a lie.
 *
 * The check reads the parsed document rather than the text, because XML has entities, CDATA,
 * namespace prefixes and every case of every name, and a rule written against the text is a rule
 * about one spelling of it. It is still a list of the things this knows to look for, which is why
 * the `Content-Security-Policy` on the served file is the defence that does not depend on it.
 */
object SvgUploads {

    /** Whether an upload claims to be a vector, which is what makes this the check that applies. */
    fun isDeclared(mediaType: String): Boolean =
        mediaType.substringBefore(';').trim().equals(VECTOR_MEDIA_TYPE, ignoreCase = true)

    /**
     * Reads [content] as an icon, throwing where it is not one. Closes what it is given.
     *
     * A file that does not parse as XML rooted at an `svg` element is not an SVG, whatever its
     * upload claimed — which is the same refusal, since a claim is not a fact.
     */
    fun enforce(content: InputStream) {
        val document = content.use { bytes ->
            try {
                parser().parse(InputSource(bytes))
            } catch (_: SAXException) {
                throw InvalidFileException(NOT_A_VECTOR)
            }
        }
        val root = document.documentElement ?: throw InvalidFileException(NOT_A_VECTOR)
        if (localNameOf(root) != "svg") throw InvalidFileException(NOT_A_VECTOR)
        walk(root)
    }

    private fun walk(node: Node) {
        if (node is Element) check(node)
        node.childNodes.each(::walk)
    }

    private fun check(element: Element) {
        when (localNameOf(element)) {
            "script" -> throw UnsafeSvgException("contains a script")
            "foreignobject" -> throw UnsafeSvgException("contains a foreignObject")
            "style" -> style(element.textContent.orEmpty())
        }

        val attributes = element.attributes
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            val name = localNameOf(attribute)
            val value = attribute.nodeValue.orEmpty()

            if (name.startsWith("on")) throw UnsafeSvgException("carries the event handler $name")
            // An animation may write any attribute it names, so one aimed at a handler is one.
            if (name == "attributename" && value.trim().lowercase(Locale.ROOT).startsWith("on")) {
                throw UnsafeSvgException("carries the event handler ${value.trim()}")
            }
            if (value.contains("javascript:", ignoreCase = true)) throw external()
            if (name in REFERENCE_ATTRIBUTES && !isLocal(value)) throw external()
            urlsIn(value).forEach { if (!isLocal(it)) throw external() }
        }
    }

    /** A stylesheet inside the document, which reaches other origins the same two ways CSS does. */
    private fun style(css: String) {
        if (css.contains("@import", ignoreCase = true)) throw external()
        urlsIn(css).forEach { if (!isLocal(it)) throw external() }
    }

    /**
     * Whether a reference stays inside this document.
     *
     * Anything else is refused, absolute or relative alike: a logo has no use for a second file,
     * and a relative reference is still a fetch from the origin that holds the session. A `data:`
     * bitmap fetches nothing, but a nested SVG is another document, so only a bitmap passes.
     */
    private fun isLocal(reference: String): Boolean {
        val target = reference.trim()
        if (target.isEmpty() || target.startsWith("#")) return true
        return target.startsWith("data:image/", ignoreCase = true) &&
            !target.startsWith("data:image/svg", ignoreCase = true)
    }

    private fun urlsIn(value: String): List<String> =
        CSS_URL.findAll(value).map { it.groupValues[2] }.toList()

    private fun localNameOf(node: Node): String =
        (node.localName ?: node.nodeName).substringAfterLast(':').lowercase(Locale.ROOT)

    private fun external() = UnsafeSvgException("points at something outside itself")

    private inline fun NodeList.each(visit: (Node) -> Unit) {
        for (index in 0 until length) visit(item(index))
    }

    /**
     * A parser that reads the document and nothing else.
     *
     * External entities and an external DTD are off, so a file cannot make the api fetch a url or
     * read a host file while being checked. Internal entities are expanded, under secure
     * processing's expansion limit, precisely so that what they expand to is walked as well.
     */
    private fun parser() = DocumentBuilderFactory.newInstance().apply {
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isNamespaceAware = true
        isXIncludeAware = false
        isExpandEntityReferences = true
    }.newDocumentBuilder().apply {
        setEntityResolver { _, _ -> InputSource("".reader()) }
        // The parser's own handler prints to stderr and carries on. A file it cannot read is
        // not an SVG, so an error is the answer rather than a note.
        setErrorHandler(object : ErrorHandler {
            override fun warning(e: SAXParseException) = Unit
            override fun error(e: SAXParseException): Unit = throw e
            override fun fatalError(e: SAXParseException): Unit = throw e
        })
    }

    private const val NOT_A_VECTOR = "That file is not an SVG."

    /** The attributes that name another document, `xlink:href` among them by its local name. */
    private val REFERENCE_ATTRIBUTES = setOf("href", "src", "data")

    private val CSS_URL = Regex("""url\(\s*(['"]?)([^)'"]*)\1\s*\)""", RegexOption.IGNORE_CASE)
}
