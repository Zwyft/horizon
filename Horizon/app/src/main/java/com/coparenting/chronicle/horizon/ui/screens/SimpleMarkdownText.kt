package com.coparenting.chronicle.horizon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Renders a subset of Markdown:
 *  - **bold** inline spans
 *  - Lines starting with "- " or "• " as bullet points
 *  - "# " / "## " headers
 *  - Blank lines as paragraph breaks
 */
@Composable
fun SimpleMarkdownText(
    text: String,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    val blocks = parseMarkdownBlocks(text)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading1 -> Text(
                    block.text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                is MdBlock.Heading2 -> Text(
                    block.text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
                is MdBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("•", style = style, color = color)
                    Text(
                        buildInlineAnnotated(block.text),
                        style = style,
                        color = color,
                        modifier = Modifier.weight(1f)
                    )
                }
                is MdBlock.Paragraph -> Text(
                    buildInlineAnnotated(block.text),
                    style = style,
                    color = color
                )
            }
        }
    }
}

private sealed class MdBlock {
    data class Heading1(val text: String) : MdBlock()
    data class Heading2(val text: String) : MdBlock()
    data class Bullet(val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val pendingLines = mutableListOf<String>()

    fun flush() {
        val joined = pendingLines.joinToString(" ").trim()
        if (joined.isNotBlank()) blocks.add(MdBlock.Paragraph(joined))
        pendingLines.clear()
    }

    for (line in raw.lines()) {
        when {
            line.startsWith("## ") -> { flush(); blocks.add(MdBlock.Heading2(line.drop(3).trim())) }
            line.startsWith("# ")  -> { flush(); blocks.add(MdBlock.Heading1(line.drop(2).trim())) }
            line.startsWith("- ")  -> { flush(); blocks.add(MdBlock.Bullet(line.drop(2).trim())) }
            line.startsWith("• ")  -> { flush(); blocks.add(MdBlock.Bullet(line.drop(2).trim())) }
            line.isBlank()         -> flush()
            else                   -> pendingLines.add(line)
        }
    }
    flush()
    return blocks
}

private val inlinePattern = Regex("""\*\*(.+?)\*\*|\*(.+?)\*""")

private fun buildInlineAnnotated(text: String) = buildAnnotatedString {
    var cursor = 0
    inlinePattern.findAll(text).forEach { match ->
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        if (match.value.startsWith("**")) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
        } else {
            append(match.groupValues[2])
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}
