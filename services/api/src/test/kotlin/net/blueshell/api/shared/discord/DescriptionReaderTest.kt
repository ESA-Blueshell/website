package net.blueshell.api.shared.discord

import net.blueshell.api.shared.discord.DescriptionNodeKind.CELL
import net.blueshell.api.shared.discord.DescriptionNodeKind.CHANNEL_MENTION
import net.blueshell.api.shared.discord.DescriptionNodeKind.CODE
import net.blueshell.api.shared.discord.DescriptionNodeKind.CODE_BLOCK
import net.blueshell.api.shared.discord.DescriptionNodeKind.EMOJI
import net.blueshell.api.shared.discord.DescriptionNodeKind.HEADING
import net.blueshell.api.shared.discord.DescriptionNodeKind.IMAGE
import net.blueshell.api.shared.discord.DescriptionNodeKind.ITEM
import net.blueshell.api.shared.discord.DescriptionNodeKind.LINE_BREAK
import net.blueshell.api.shared.discord.DescriptionNodeKind.LINK
import net.blueshell.api.shared.discord.DescriptionNodeKind.LIST
import net.blueshell.api.shared.discord.DescriptionNodeKind.PARAGRAPH
import net.blueshell.api.shared.discord.DescriptionNodeKind.QUOTE
import net.blueshell.api.shared.discord.DescriptionNodeKind.ROLE_MENTION
import net.blueshell.api.shared.discord.DescriptionNodeKind.ROW
import net.blueshell.api.shared.discord.DescriptionNodeKind.SERVER_EMOJI
import net.blueshell.api.shared.discord.DescriptionNodeKind.STRONG
import net.blueshell.api.shared.discord.DescriptionNodeKind.SUBTEXT
import net.blueshell.api.shared.discord.DescriptionNodeKind.TABLE
import net.blueshell.api.shared.discord.DescriptionNodeKind.TEXT
import net.blueshell.api.shared.discord.DescriptionNodeKind.TIMESTAMP
import net.blueshell.api.shared.discord.DescriptionNodeKind.USER_MENTION
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DescriptionReaderTest {
    private fun read(text: String) = DescriptionReader.read(text).nodes

    // A tree written out short: a kind, its text in quotes and its children in brackets.
    private fun shape(nodes: List<DescriptionNode>): String =
        nodes.joinToString(" ") { node ->
            buildString {
                append(node.kind.name)
                node.text?.let { append("\"").append(it).append("\"") }
                if (node.children.isNotEmpty()) append("(").append(shape(node.children)).append(")")
            }
        }

    private fun shapeOf(text: String) = shape(read(text))

    // Every node with the text it was read from, depth first.
    private fun sources(text: String): List<String> {
        val found = mutableListOf<String>()

        fun walk(nodes: List<DescriptionNode>) {
            for (node in nodes) {
                found += "${node.kind}:${text.substring(node.start, node.end)}"
                walk(node.children)
            }
        }
        walk(read(text))
        return found
    }

    @Test
    fun `bolds a span that ends in a space, as Discord does`() {
        assertThat(shapeOf("**Sign ups required! **")).isEqualTo("PARAGRAPH(STRONG(TEXT\"Sign ups required! \"))")
    }

    @Test
    fun `underlines between double underscores, and italicises between single ones`() {
        assertThat(shapeOf("__under__ and _slanted_"))
            .isEqualTo("PARAGRAPH(UNDERLINE(TEXT\"under\") TEXT\" and \" EMPHASIS(TEXT\"slanted\"))")
    }

    @Test
    fun `leaves underscores inside a word alone`() {
        assertThat(shapeOf("snake_case_name")).isEqualTo("PARAGRAPH(TEXT\"snake_case_name\")")
        assertThat(shapeOf("a_b_")).isEqualTo("PARAGRAPH(TEXT\"a_b_\")")
    }

    @Test
    fun `reads three stars as bold around italic`() {
        assertThat(shapeOf("***both***")).isEqualTo("PARAGRAPH(STRONG(EMPHASIS(TEXT\"both\")))")
    }

    @Test
    fun `strikes through only between double tildes`() {
        assertThat(shapeOf("~~gone~~ but ~kept~")).isEqualTo("PARAGRAPH(STRIKE(TEXT\"gone\") TEXT\" but ~kept~\")")
    }

    @Test
    fun `hides a spoiler, formatting and all`() {
        assertThat(shapeOf("the end: ||he **wins**||"))
            .isEqualTo("PARAGRAPH(TEXT\"the end: \" SPOILER(TEXT\"he \" STRONG(TEXT\"wins\")))")
    }

    @Test
    fun `sets subtext apart, with its own formatting, even straight after a line of text`() {
        assertThat(shapeOf("above\n-# small **print**\nbelow"))
            .isEqualTo("PARAGRAPH(TEXT\"above\") SUBTEXT(TEXT\"small \" STRONG(TEXT\"print\")) PARAGRAPH(TEXT\"below\")")
    }

    @Test
    fun `breaks a line wherever the text does`() {
        assertThat(shapeOf("one\ntwo")).isEqualTo("PARAGRAPH(TEXT\"one\" LINE_BREAK TEXT\"two\")")
    }

    @Test
    fun `reads headings, but a hash against a word is no heading`() {
        val heading = read("## Rules\n#general").first()

        assertThat(heading.kind).isEqualTo(HEADING)
        assertThat(heading.level).isEqualTo(2)
        assertThat(shapeOf("#general")).isEqualTo("PARAGRAPH(TEXT\"#general\")")
    }

    @Test
    fun `quotes everything after three angle brackets`() {
        assertThat(shapeOf("intro\n>>> one\n\ntwo"))
            .isEqualTo("PARAGRAPH(TEXT\"intro\") QUOTE(PARAGRAPH(TEXT\"one\") PARAGRAPH(TEXT\"two\"))")
        assertThat(shapeOf("> one\n\nafter")).isEqualTo("QUOTE(PARAGRAPH(TEXT\"one\")) PARAGRAPH(TEXT\"after\")")
    }

    @Test
    fun `ends a list at the first line that is not an item`() {
        assertThat(shapeOf("- one\nafter")).isEqualTo("LIST(ITEM(PARAGRAPH(TEXT\"one\"))) PARAGRAPH(TEXT\"after\")")
        assertThat(shapeOf("- one\n  under one\nafter"))
            .isEqualTo("LIST(ITEM(PARAGRAPH(TEXT\"one\" LINE_BREAK TEXT\"under one\"))) PARAGRAPH(TEXT\"after\")")
    }

    @Test
    fun `says how a list is numbered and whether it is tight`() {
        val list = read("3. three\n4. four").single()

        assertThat(list.kind).isEqualTo(LIST)
        assertThat(list.ordered).isTrue()
        assertThat(list.number).isEqualTo(3)
        assertThat(list.tight).isTrue()
        assertThat(list.children.map { it.kind }).containsExactly(ITEM, ITEM)
    }

    @Test
    fun `leaves lines inside a code block as they are`() {
        val code = read("```kotlin\n- one\n>>> not a quote\n```").single()

        assertThat(code.kind).isEqualTo(CODE_BLOCK)
        assertThat(code.language).isEqualTo("kotlin")
        assertThat(code.text).isEqualTo("- one\n>>> not a quote\n")
    }

    @Test
    fun `keeps tables, with their head and alignment`() {
        val table = read("| a | b | c | g |\n| :-: | :-- | --: | - |\n| d | e | f | h |").single()

        assertThat(table.kind).isEqualTo(TABLE)
        assertThat(table.children.map { it.kind }).containsExactly(ROW, ROW)
        val head =
            table.children
                .first()
                .children
                .first()
        assertThat(head.kind).isEqualTo(CELL)
        assertThat(head.header).isTrue()
        assertThat(
            table.children
                .first()
                .children
                .map { it.align },
        ).containsExactly(DescriptionCellAlign.CENTER, DescriptionCellAlign.LEFT, DescriptionCellAlign.RIGHT, null)
        assertThat(
            table.children
                .last()
                .children
                .first()
                .header,
        ).isFalse()
    }

    @Test
    fun `keeps pictures and links`() {
        val picture = read("![map](https://x.io/map.png)").single().children.single()
        val link = read("[site](https://blueshell.utwente.nl \"home\")").single().children.single()

        assertThat(picture.kind).isEqualTo(IMAGE)
        assertThat(picture.href).isEqualTo("https://x.io/map.png")
        assertThat(picture.text).isEqualTo("map")
        assertThat(link.kind).isEqualTo(LINK)
        assertThat(link.href).isEqualTo("https://blueshell.utwente.nl")
        assertThat(link.title).isEqualTo("home")
        assertThat(shape(link.children)).isEqualTo("TEXT\"site\"")
    }

    @Test
    fun `links addresses written out, and stops before the punctuation after one`() {
        val read = read("see https://x.io/a:fire:b, or www.x.io and mail a@b.nl.").single().children

        assertThat(read.filter { it.kind == LINK }.map { it.href })
            .containsExactly("https://x.io/a:fire:b", "http://www.x.io", "mailto:a@b.nl")
        assertThat(read.last().text).isEqualTo(".")
    }

    @Test
    fun `links an address in angle brackets, but no script`() {
        assertThat(
            read("<https://x.io> <a@b.nl>")
                .single()
                .children
                .filter { it.kind == LINK }
                .map { it.href },
        ).containsExactly("https://x.io", "mailto:a@b.nl")
        assertThat(shapeOf("<javascript:alert(1)>")).isEqualTo("PARAGRAPH(TEXT\"<javascript:alert(1)>\")")
    }

    @Test
    fun `leaves out the bracket after an address it did not open`() {
        assertThat(
            read("(see https://x.io/a_(b)).")
                .single()
                .children
                .single { it.kind == LINK }
                .href,
        ).isEqualTo("https://x.io/a_(b)")
    }

    @Test
    fun `links by a label a definition names, and leaves a label nobody defined as written`() {
        val defined = "[site][home] and [home]\n\n[home]: https://x.io \"Home\""

        assertThat(
            read(defined)
                .single()
                .children
                .filter { it.kind == LINK }
                .map { it.href to it.title },
        ).containsExactly("https://x.io" to "Home", "https://x.io" to "Home")
        assertThat(shapeOf("[site][nowhere]")).isEqualTo("PARAGRAPH(TEXT\"[site][nowhere]\")")
    }

    @Test
    fun `finds the end of a label past escaped brackets and code`() {
        val link = read("[a \\] `]` b](https://x.io) and [open").single().children

        assertThat(shape(link)).isEqualTo("LINK(TEXT\"a ] \" CODE\"]\" TEXT\" b\") TEXT\" and [open\"")
    }

    @Test
    fun `leaves backticks nobody closes as written`() {
        assertThat(shapeOf("``a` b")).isEqualTo("PARAGRAPH(TEXT\"``a` b\")")
    }

    @Test
    fun `never links a script`() {
        assertThat(shapeOf("[x](javascript:alert(1))")).isEqualTo("PARAGRAPH(TEXT\"x\")")
    }

    @Test
    fun `shows html as the text it is`() {
        assertThat(shapeOf("<script>alert('x')</script>")).isEqualTo("PARAGRAPH(TEXT\"<script>alert('x')</script>\")")
    }

    @Test
    fun `keeps code as written`() {
        assertThat(shapeOf("`:fire: 🔥 <@123456789012345678>` and ``a`b``"))
            .isEqualTo("PARAGRAPH(CODE\":fire: 🔥 <@123456789012345678>\" TEXT\" and \" CODE\"a`b\")")
    }

    @Test
    fun `reads an escaped mark as the mark, and an entity as written, as Discord does`() {
        assertThat(shapeOf("\\*not italic\\* &amp; more")).isEqualTo("PARAGRAPH(TEXT\"*not italic* &amp; more\")")
    }

    @Test
    fun `reads each emoji presented as one, and leaves characters that are text by default as text`() {
        val read = read("hot 🔥 👍🏽 🇳🇱 1️⃣ ❤️ © ™ ↔ 20:00").single().children

        assertThat(read.filter { it.kind == EMOJI }.map { it.text }).containsExactly("🔥", "👍🏽", "🇳🇱", "1️⃣", "❤️")
        assertThat(read.last().text).isEqualTo(" © ™ ↔ 20:00")
    }

    @Test
    fun `leaves a shortcode as the text it is`() {
        assertThat(shapeOf(":rocket:")).isEqualTo("PARAGRAPH(TEXT\":rocket:\")")
    }

    @Test
    fun `reads a server's emoji, from this server or any other`() {
        val (still, moving) =
            read("gg <:POGGERS:657733730491826186> and <a:party:123456789012345678>")
                .single()
                .children
                .filter { it.kind == SERVER_EMOJI }

        assertThat(still.name).isEqualTo("POGGERS")
        assertThat(still.id).isEqualTo("657733730491826186")
        assertThat(still.animated).isFalse()
        assertThat(moving.animated).isTrue()
    }

    @Test
    fun `reads members, roles and channels by their IDs`() {
        val read =
            read("<@123456789012345678> <@!123456789012345679> <@&223456789012345678> <#323456789012345678>")
                .single()
                .children
                .filter { it.kind != TEXT }

        assertThat(read.map { it.kind }).containsExactly(USER_MENTION, USER_MENTION, ROLE_MENTION, CHANNEL_MENTION)
        assertThat(read.map { it.id })
            .containsExactly("123456789012345678", "123456789012345679", "223456789012345678", "323456789012345678")
    }

    @Test
    fun `reads a timestamp in the style written, or the short date and time`() {
        val (relative, plain) = read("<t:1790000000:R> and <t:1790000000>").single().children.filter { it.kind == TIMESTAMP }

        assertThat(relative.unix).isEqualTo(1790000000)
        assertThat(relative.style).isEqualTo(DescriptionTimeStyle.RELATIVE)
        assertThat(plain.style).isEqualTo(DescriptionTimeStyle.SHORT_DATE_TIME)
    }

    @Test
    fun `leaves what only looks like a mention or a timestamp as written`() {
        assertThat(shapeOf("<t:soon> <@me>")).isEqualTo("PARAGRAPH(TEXT\"<t:soon> <@me>\")")
    }

    @Test
    fun `says where every node was read from`() {
        assertThat(sources("Hi **there** 🔥\n> <@123456789012345678>"))
            .containsExactly(
                "PARAGRAPH:Hi **there** 🔥",
                "TEXT:Hi ",
                "STRONG:**there**",
                "TEXT:there",
                "TEXT: ",
                "EMOJI:🔥",
                "QUOTE:> <@123456789012345678>",
                "PARAGRAPH:<@123456789012345678>",
                "USER_MENTION:<@123456789012345678>",
            )
    }

    @Test
    fun `says where a node was read from where the dialect reads the text differently`() {
        assertThat(sources("- one\nafter\n>>> quoted\nrest"))
            .containsExactly(
                "LIST:- one",
                "ITEM:- one",
                "PARAGRAPH:one",
                "TEXT:one",
                "PARAGRAPH:after",
                "TEXT:after",
                "QUOTE:>>> quoted\nrest",
                "PARAGRAPH:quoted\nrest",
                "TEXT:quoted",
                "LINE_BREAK:\n",
                "TEXT:rest",
            )
        assertThat(sources("a \\* b")).containsExactly("PARAGRAPH:a \\* b", "TEXT:a \\* b")
    }

    @Test
    fun `reads a long paragraph that opens a mark it never closes`() {
        val words = "word ".repeat(4000)

        for (opening in listOf(" _note ", "*x", "[a](", "[a](b \"", "**", "__", "||", "~~", "`", "<", "www.x")) {
            val read = read(opening + words)

            assertThat(read.single().end).isEqualTo(opening.length + words.length)
        }
    }

    @Test
    fun `reads a long paragraph of marks that each close`() {
        val text = "_a_ *b* [c](https://x.io/d \"e\") ".repeat(800)

        assertThat(read(text).single().children.count { it.kind == LINK }).isEqualTo(800)
    }

    @Test
    fun `reads italics as Discord does at their edges`() {
        assertThat(shapeOf("*a **b** c*")).isEqualTo("PARAGRAPH(EMPHASIS(TEXT\"a \" STRONG(TEXT\"b\") TEXT\" c\"))")
        assertThat(shapeOf("*a \\* b*")).isEqualTo("PARAGRAPH(EMPHASIS(TEXT\"a * b\"))")
        assertThat(shapeOf("x * a*")).isEqualTo("PARAGRAPH(TEXT\"x * a*\")")
        assertThat(shapeOf("*a *")).isEqualTo("PARAGRAPH(TEXT\"*a *\")")
        assertThat(shapeOf("_a __b__ c_ and _x_y"))
            .isEqualTo("PARAGRAPH(EMPHASIS(TEXT\"a \" UNDERLINE(TEXT\"b\") TEXT\" c\") TEXT\" and _x_y\")")
        assertThat(shapeOf("_a \\_ b_")).isEqualTo("PARAGRAPH(EMPHASIS(TEXT\"a _ b\"))")
    }

    @Test
    fun `reads a link's target in any of its forms`() {
        val links = read("[a](<x y>) [b](c(d)e 'f') [g]( h (i) ) [j](k\\)l)").single().children.filter { it.kind == LINK }

        assertThat(links.map { it.href to it.title })
            .containsExactly("x y" to null, "c(d)e" to "f", "h" to "i", "k)l" to null)
        assertThat(
            read("[a](b \"c \\\" d\")")
                .single()
                .children
                .single()
                .title,
        ).isEqualTo("c \" d")
        assertThat(shapeOf("[a](b (c(d)))")).isEqualTo("PARAGRAPH(TEXT\"[a](b (c(d)))\")")
        assertThat(shapeOf("[a](b \"c\" d)")).isEqualTo("PARAGRAPH(TEXT\"[a](b \"c\" d)\")")
        assertThat(shapeOf("[a](b \"c\\")).isEqualTo("PARAGRAPH(TEXT\"[a](b \"c\\\")")
    }

    @Test
    fun `counts the text as stored`() {
        assertThat(DescriptionReader.read("a <:sitecie:123456789012345678>").length).isEqualTo(31)
    }

    @Test
    fun `reads a description of the kind members write`() {
        val text =
            """
            # Blueshell LAN Party 🎮
            Join us for a weekend of **games**, __snacks__ and ||a surprise||!

            - Bring your own PC
            - Sign up before <t:1790000000:D>
              (members get in free)
            Questions? Ask <@&223456789012345678> in <#323456789012345678> <:blueshell:657733730491826186>
            -# Organised by the LAN committee
            """.trimIndent()

        assertThat(read(text).map { it.kind }).containsExactly(HEADING, PARAGRAPH, LIST, PARAGRAPH, SUBTEXT)
        assertThat(
            read(text)[2]
                .children
                .last()
                .children
                .single()
                .children
                .map { it.kind },
        ).containsExactly(TEXT, TIMESTAMP, LINE_BREAK, TEXT)
        assertThat(read(text)[3].children.map { it.kind })
            .containsExactly(TEXT, ROLE_MENTION, TEXT, CHANNEL_MENTION, TEXT, SERVER_EMOJI)
        assertThat(read(text)[0].children.last().kind).isEqualTo(EMOJI)
        assertThat(read(text)[1].children.map { it.kind }).contains(STRONG)
        assertThat(sources(text).filter { it.startsWith("$CODE:") }).isEmpty()
        assertThat(read(text).single { it.kind == SUBTEXT }.children.map { it.kind }).containsExactly(TEXT)
        assertThat(read(text).single { it.kind == LIST }.children.map { it.kind }).containsExactly(ITEM, ITEM)
        assertThat(read(text).map { it.kind }).doesNotContain(QUOTE, LINE_BREAK)
    }
}
