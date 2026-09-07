package net.blueshell.api.file.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException

/**
 * What an icon may be, and what it is refused for.
 *
 * Every refusal is asserted through the sentence it answers with as well as the refusal itself:
 * whoever chose the file is the only person who can choose another one, so a reason they cannot
 * act on is a failure of its own.
 *
 * The obfuscated spellings are here on purpose. They are the reason this reads a parsed document
 * rather than the text — and they are also the reason the served file carries a policy that runs
 * no script, since a list of known shapes is a list somebody can be cleverer than.
 */
class SvgUploadsTest {

    private fun refusalOf(svg: String): ResponseStatusException {
        val thrown = runCatching { SvgUploads.enforce(svg.byteInputStream()) }.exceptionOrNull()
        assertThat(thrown).describedAs(svg).isInstanceOf(ResponseStatusException::class.java)
        return thrown as ResponseStatusException
    }

    private fun accepts(svg: String) =
        assertThatCode { SvgUploads.enforce(svg.byteInputStream()) }.doesNotThrowAnyException()

    @Test
    fun `a vector claim is read from the media type it arrived under`() {
        assertThat(SvgUploads.isDeclared("image/svg+xml")).isTrue()
        assertThat(SvgUploads.isDeclared("IMAGE/SVG+XML; charset=utf-8")).isTrue()
        assertThat(SvgUploads.isDeclared("image/png")).isFalse()
    }

    @Test
    fun `a logo of shapes and flat colour is what this admits`() {
        accepts(
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
              <defs><linearGradient id="g"><stop offset="0" stop-color="#0af"/></linearGradient></defs>
              <style>.mark { fill: url(#g); }</style>
              <path class="mark" d="M2 2h20v20H2z"/>
              <use href="#g"/>
            </svg>
            """.trimIndent(),
        )
    }

    @Test
    fun `a script is refused, and named`() {
        val refusal = refusalOf(
            """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>""",
        )
        assertThat(refusal.reason).isEqualTo("That SVG contains a script, which an icon cannot.")
    }

    @Test
    fun `an event handler is refused, and named`() {
        val refusal = refusalOf("""<svg xmlns="http://www.w3.org/2000/svg" onload="alert(1)"/>""")
        assertThat(refusal.reason).isEqualTo("That SVG carries the event handler onload, which an icon cannot.")
    }

    @Test
    fun `a foreignObject is refused, and named`() {
        val refusal = refusalOf(
            """<svg xmlns="http://www.w3.org/2000/svg"><foreignObject width="1" height="1"/></svg>""",
        )
        assertThat(refusal.reason).isEqualTo("That SVG contains a foreignObject, which an icon cannot.")
    }

    @Test
    fun `a reference to another url is refused, and named`() {
        val refusal = refusalOf(
            """<svg xmlns="http://www.w3.org/2000/svg"><image href="https://elsewhere.example/x.png"/></svg>""",
        )
        assertThat(refusal.reason).isEqualTo("That SVG points at something outside itself, which an icon cannot.")
    }

    /** The three other ways a document reaches off itself, each refused as the first one is. */
    @Test
    fun `every other way of reaching another url is refused too`() {
        val ways = listOf(
            """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
                 <use xlink:href="//elsewhere.example/x.svg#a"/></svg>""",
            """<svg xmlns="http://www.w3.org/2000/svg"><style>@import url(x.css);</style></svg>""",
            """<svg xmlns="http://www.w3.org/2000/svg"><rect style="fill: url(http://e.example/p)"/></svg>""",
            """<svg xmlns="http://www.w3.org/2000/svg"><a href="javascript:alert(1)"><rect/></a></svg>""",
            """<svg xmlns="http://www.w3.org/2000/svg"><use href="/files/public/game-icons/other.svg"/></svg>""",
        )
        ways.forEach { assertThat(refusalOf(it).reason).contains("outside itself") }
    }

    /** A bitmap in the file itself fetches nothing, so an icon that carries one is still an icon. */
    @Test
    fun `an embedded bitmap is not a reference to somewhere else`() {
        accepts(
            """<svg xmlns="http://www.w3.org/2000/svg"><image href="data:image/png;base64,iVBORw0K"/></svg>""",
        )
    }

    /** A nested document is a document. The bytes are inline, but what they are is another SVG. */
    @Test
    fun `an SVG carried inside a data url is refused`() {
        assertThat(
            refusalOf(
                """<svg xmlns="http://www.w3.org/2000/svg"><image href="data:image/svg+xml;base64,PHN2Zz4="/></svg>""",
            ).reason,
        ).contains("outside itself")
    }

    @Test
    fun `a handler an animation would write is a handler`() {
        assertThat(
            refusalOf(
                """<svg xmlns="http://www.w3.org/2000/svg">
                     <set attributeName="onmouseover" to="alert(1)"/></svg>""",
            ).reason,
        ).contains("event handler")
    }

    /**
     * The spellings a text search misses.
     *
     * An uppercase element name and an uppercase attribute are the same node to a parser; a
     * script inside CDATA is still a script element; and an internal entity is expanded before
     * anything is walked, so what it expands to is what is judged.
     */
    @Test
    fun `an obfuscated script is the same script`() {
        val spellings = listOf(
            """<svg xmlns="http://www.w3.org/2000/svg"><SCRIPT>alert(1)</SCRIPT></svg>""",
            """<svg xmlns="http://www.w3.org/2000/svg"><script><![CDATA[alert(1)]]></script></svg>""",
            """<svg xmlns="http://www.w3.org/2000/svg" ONLOAD="alert(1)"/>""",
            """<?xml version="1.0"?>
               <!DOCTYPE svg [<!ENTITY hidden "<script>alert(1)</script>">]>
               <svg xmlns="http://www.w3.org/2000/svg">&hidden;</svg>""",
        )
        // Refused, whichever refusal it earns: an entity a parser will not expand at all leaves
        // a file that cannot be read as an SVG, which is the same answer by another route.
        spellings.forEach { assertThat(refusalOf(it).statusCode.value()).isEqualTo(400) }
    }

    @Test
    fun `a file claiming to be a vector whose bytes are not one is refused`() {
        listOf(
            "<html><body><script>alert(1)</script></body></html>",
            "PNG\r\n\n",
            "",
            "<svg><unclosed>",
        ).forEach { assertThat(refusalOf(it).reason).isEqualTo("That file is not an SVG.") }
    }
}
