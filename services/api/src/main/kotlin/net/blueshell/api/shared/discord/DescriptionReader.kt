package net.blueshell.api.shared.discord

import net.blueshell.api.shared.discord.DescriptionNodeKind.CELL
import net.blueshell.api.shared.discord.DescriptionNodeKind.CODE_BLOCK
import net.blueshell.api.shared.discord.DescriptionNodeKind.HEADING
import net.blueshell.api.shared.discord.DescriptionNodeKind.ITEM
import net.blueshell.api.shared.discord.DescriptionNodeKind.LIST
import net.blueshell.api.shared.discord.DescriptionNodeKind.PARAGRAPH
import net.blueshell.api.shared.discord.DescriptionNodeKind.QUOTE
import net.blueshell.api.shared.discord.DescriptionNodeKind.ROW
import net.blueshell.api.shared.discord.DescriptionNodeKind.RULE
import net.blueshell.api.shared.discord.DescriptionNodeKind.SUBTEXT
import net.blueshell.api.shared.discord.DescriptionNodeKind.TABLE
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.InlineParser
import org.commonmark.parser.InlineParserFactory
import org.commonmark.parser.Parser
import org.commonmark.parser.SourceLine
import org.commonmark.parser.SourceLines
import org.commonmark.parser.block.AbstractBlockParser
import org.commonmark.parser.block.AbstractBlockParserFactory
import org.commonmark.parser.block.BlockContinue
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState

/**
 * Reads a description into the tree every place it is shown is made from (architecture ADR-010).
 * Blocks are read as CommonMark reads them, with Discord's `-# ` subtext and its two line rules;
 * the text inside them as Discord does, by [DiscordInlines]. HTML is not read: it is the text it is.
 */
object DescriptionReader {
    private val parser =
        Parser
            .builder()
            .extensions(listOf(TablesExtension.create()))
            .enabledBlockTypes(
                setOf(
                    Heading::class.java,
                    FencedCodeBlock::class.java,
                    IndentedCodeBlock::class.java,
                    BlockQuote::class.java,
                    ThematicBreak::class.java,
                    ListBlock::class.java,
                ),
            ).customBlockParserFactory(SubtextStart)
            .inlineParserFactory(
                InlineParserFactory { context -> InlineParser { lines, block -> block.appendChild(Inlines(context, lines)) } },
            ).includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build()

    fun read(text: String): DescriptionTree {
        val lines = DiscordLines.of(text)
        val nodes = blocksOf(parser.parse(lines.text)).map { it.placed(lines::original) }
        return DescriptionTree(nodes, text.length)
    }

    private fun DescriptionNode.placed(original: (Int) -> Int): DescriptionNode =
        copy(start = original(start), end = original(end), children = children.map { it.placed(original) })

    private fun blocksOf(parent: Node): List<DescriptionNode> = childrenOf(parent).flatMap { blockOf(it) }

    private fun childrenOf(parent: Node): List<Node> = generateSequence(parent.firstChild) { it.next }.toList()

    private fun blockOf(node: Node): List<DescriptionNode> =
        when (node) {
            is Paragraph -> listOf(nodeOf(PARAGRAPH, node, inlinesOf(node)))
            is Heading -> listOf(nodeOf(HEADING, node, inlinesOf(node)).copy(level = node.level))
            is Subtext -> listOf(nodeOf(SUBTEXT, node, inlinesOf(node)))
            is BlockQuote -> listOf(nodeOf(QUOTE, node, blocksOf(node)))
            is BulletList -> listOf(nodeOf(LIST, node, blocksOf(node)).copy(ordered = false, tight = node.isTight))
            is OrderedList ->
                listOf(nodeOf(LIST, node, blocksOf(node)).copy(ordered = true, number = node.markerStartNumber, tight = node.isTight))
            is ListItem -> listOf(nodeOf(ITEM, node, blocksOf(node)))
            is FencedCodeBlock ->
                listOf(
                    nodeOf(CODE_BLOCK, node).copy(
                        text = node.literal,
                        language = node.info?.substringBefore(' ')?.ifBlank { null },
                    ),
                )
            is IndentedCodeBlock -> listOf(nodeOf(CODE_BLOCK, node).copy(text = node.literal))
            is ThematicBreak -> listOf(nodeOf(RULE, node))
            is TableBlock -> listOf(nodeOf(TABLE, node, childrenOf(node).flatMap { blocksOf(it) }))
            is TableRow -> listOf(nodeOf(ROW, node, blocksOf(node)))
            is TableCell -> listOf(nodeOf(CELL, node, inlinesOf(node)).copy(header = node.isHeader, align = alignOf(node.alignment)))
            else -> emptyList()
        }

    private fun alignOf(alignment: TableCell.Alignment?) =
        when (alignment) {
            TableCell.Alignment.LEFT -> DescriptionCellAlign.LEFT
            TableCell.Alignment.CENTER -> DescriptionCellAlign.CENTER
            TableCell.Alignment.RIGHT -> DescriptionCellAlign.RIGHT
            null -> null
        }

    private fun inlinesOf(block: Node): List<DescriptionNode> = childrenOf(block).filterIsInstance<Inlines>().flatMap { it.nodes }

    private fun nodeOf(
        kind: DescriptionNodeKind,
        node: Node,
        children: List<DescriptionNode> = emptyList(),
    ): DescriptionNode {
        val spans = node.sourceSpans
        val start = spans.firstOrNull()?.inputIndex ?: children.firstOrNull()?.start ?: 0
        val end = spans.lastOrNull()?.let { it.inputIndex + it.length } ?: children.lastOrNull()?.end ?: start
        return DescriptionNode(kind, start, end, children)
    }

    /** The text of a block, read by [DiscordInlines] and placed in the text the block came from. */
    private class Inlines(
        context: org.commonmark.parser.InlineParserContext,
        lines: SourceLines,
    ) : CustomNode() {
        val nodes: List<DescriptionNode> = DiscordInlines(context, lines).let { it.placed(it.read()) }
    }

    /** `-# ` and a line of small print, which Discord ends at the line's end. */
    private class Subtext : CustomBlock()

    private class SubtextParser(
        private val line: SourceLine,
    ) : AbstractBlockParser() {
        private val block = Subtext()

        override fun getBlock() = block

        override fun tryContinue(state: ParserState): BlockContinue? = BlockContinue.none()

        override fun parseInlines(inlineParser: InlineParser) = inlineParser.parse(SourceLines.of(line), block)
    }

    private object SubtextStart : AbstractBlockParserFactory() {
        private const val MARK = "-# "

        override fun tryStart(
            state: ParserState,
            matchedBlockParser: MatchedBlockParser,
        ): BlockStart? {
            val line = state.line
            val from = state.nextNonSpaceIndex
            if (state.indent > 0 || !line.content.startsWith(MARK, from)) return BlockStart.none()
            return BlockStart.of(SubtextParser(line.substring(from + MARK.length, line.content.length))).atIndex(line.content.length)
        }
    }
}
