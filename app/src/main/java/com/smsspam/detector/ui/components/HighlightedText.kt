package com.smsspam.detector.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

/**
 * Renders [text] with every occurrence of [query] highlighted, so search results
 * show *why* they matched. Matching is case-insensitive, and as a fallback for
 * phone numbers it also matches on digits alone, so typing `0933` highlights the
 * `933` inside `+98933…`. A blank [query] renders [text] unchanged.
 */
@Composable
fun HighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    highlightColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    val needle = query.trim()
    val annotated = remember(text, needle, highlightColor) {
        val ranges = highlightRanges(text, needle)
        if (ranges.isEmpty()) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                var cursor = 0
                ranges.forEach { range ->
                    if (range.first > cursor) append(text.substring(cursor, range.first))
                    withStyle(SpanStyle(background = highlightColor)) {
                        append(text.substring(range.first, range.last + 1))
                    }
                    cursor = range.last + 1
                }
                if (cursor < text.length) append(text.substring(cursor))
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** Raw index ranges of [text] to highlight for [needle]. */
private fun highlightRanges(text: String, needle: String): List<IntRange> {
    if (needle.isEmpty()) return emptyList()

    val plain = ArrayList<IntRange>()
    var start = 0
    while (true) {
        val index = text.indexOf(needle, start, ignoreCase = true)
        if (index < 0) break
        plain += index until (index + needle.length)
        start = index + needle.length
    }
    if (plain.isNotEmpty()) return plain

    return digitRange(text, needle)?.let { listOf(it) } ?: emptyList()
}

/**
 * Fallback for phone numbers: locates the query's digits inside [text]'s digits,
 * ignoring a leading trunk zero or an Iranian `98` country code, and returns the
 * matching raw index range.
 */
private fun digitRange(text: String, needle: String): IntRange? {
    val queryDigits = needle.filter { it.isDigit() }
    if (queryDigits.length < 2) return null

    val positions = ArrayList<Int>()
    val digits = StringBuilder()
    text.forEachIndexed { index, char ->
        if (char.isDigit()) {
            positions += index
            digits.append(char)
        }
    }
    if (digits.isEmpty()) return null

    val candidates = listOf(
        queryDigits,
        queryDigits.trimStart('0'),
        queryDigits.removePrefix("98"),
    ).distinct().filter { it.length >= 2 }

    for (candidate in candidates) {
        val at = digits.indexOf(candidate)
        if (at >= 0) {
            return positions[at]..positions[at + candidate.length - 1]
        }
    }
    return null
}
