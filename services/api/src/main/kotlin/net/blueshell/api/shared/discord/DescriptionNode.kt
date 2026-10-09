package net.blueshell.api.shared.discord

import io.swagger.v3.oas.annotations.media.Schema

/** What a piece of a description is, as Discord's markdown and the site's tables and images read it. */
@Schema(name = "DescriptionNodeKind", enumAsRef = true)
enum class DescriptionNodeKind {
    PARAGRAPH,
    HEADING,
    SUBTEXT,
    QUOTE,
    LIST,
    ITEM,
    CODE_BLOCK,
    RULE,
    TABLE,
    ROW,
    CELL,
    TEXT,
    LINE_BREAK,
    STRONG,
    EMPHASIS,
    UNDERLINE,
    STRIKE,
    SPOILER,
    CODE,
    LINK,
    IMAGE,
    EMOJI,
    SERVER_EMOJI,
    USER_MENTION,
    ROLE_MENTION,
    CHANNEL_MENTION,
    TIMESTAMP,
}

/** How a timestamp is shown, each one of Discord's styles. */
@Schema(name = "DescriptionTimeStyle", enumAsRef = true)
enum class DescriptionTimeStyle(
    val letter: Char,
) {
    SHORT_TIME('t'),
    LONG_TIME('T'),
    SHORT_DATE('d'),
    LONG_DATE('D'),
    SHORT_DATE_TIME('f'),
    LONG_DATE_TIME('F'),
    RELATIVE('R'),
    ;

    companion object {
        /** The style a letter writes, or the short date and time where none is written. */
        fun of(letter: String?): DescriptionTimeStyle = entries.firstOrNull { it.letter.toString() == letter } ?: SHORT_DATE_TIME
    }
}

@Schema(name = "DescriptionCellAlign", enumAsRef = true)
enum class DescriptionCellAlign { LEFT, CENTER, RIGHT }

/**
 * One piece of a description: a block, a span of formatting around its children, or a leaf. Only
 * the fields its kind uses are set.
 */
@Schema(name = "DescriptionNode")
data class DescriptionNode(
    val kind: DescriptionNodeKind,
    @field:Schema(description = "Where the node starts in the text, counted in UTF-16 code units")
    val start: Int,
    @field:Schema(description = "Where the node ends in the text, exclusive")
    val end: Int,
    val children: List<DescriptionNode> = emptyList(),
    @field:Schema(description = "The words of a text or code, the character of an emoji, or a picture's alternative text")
    val text: String? = null,
    @field:Schema(description = "A heading's level, from 1")
    val level: Int? = null,
    val ordered: Boolean? = null,
    @field:Schema(description = "The number an ordered list counts from")
    val number: Int? = null,
    @field:Schema(description = "Whether a list's items are lines rather than paragraphs")
    val tight: Boolean? = null,
    @field:Schema(description = "Where a link leads, or where a picture is")
    val href: String? = null,
    val title: String? = null,
    @field:Schema(description = "The language a code block names")
    val language: String? = null,
    @field:Schema(description = "Whether a cell heads its column")
    val header: Boolean? = null,
    val align: DescriptionCellAlign? = null,
    @field:Schema(description = "The Discord ID a mention or a server emoji names")
    val id: String? = null,
    @field:Schema(description = "A server emoji's name")
    val name: String? = null,
    @field:Schema(description = "Whether a server emoji moves")
    val animated: Boolean? = null,
    @field:Schema(description = "A timestamp's moment, in seconds since 1970")
    val unix: Long? = null,
    val style: DescriptionTimeStyle? = null,
)

/** A description as read, and how long it is as stored. */
@Schema(name = "DescriptionTree")
data class DescriptionTree(
    val nodes: List<DescriptionNode>,
    @field:Schema(description = "The description's length as stored, which the cap counts")
    val length: Int,
)
