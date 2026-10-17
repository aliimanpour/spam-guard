package com.smsspam.detector.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val AvatarColors = listOf(
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFE53935),
    Color(0xFF8E24AA),
    Color(0xFFFB8C00),
    Color(0xFF00897B),
    Color(0xFF3949AB),
    Color(0xFF6D4C41),
    Color(0xFF00ACC1),
    Color(0xFF7CB342),
)

/**
 * Up to two characters shown inside a [ContactAvatar]: initials for names, or
 * the last two digits for a bare phone number.
 */
fun avatarInitials(label: String): String {
    val trimmed = label.trim()
    if (trimmed.isEmpty()) return "#"
    if (trimmed.none { it.isLetter() }) {
        return trimmed.filter { it.isDigit() }.takeLast(2).ifEmpty { "#" }
    }
    val parts = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> (parts[0].first().toString() + parts[1].first()).uppercase()
        else -> parts[0].take(2).uppercase()
    }
}

/** Colored circle avatar with initials, used across the conversation UI. */
@Composable
fun ContactAvatar(
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val color = remember(label) { AvatarColors[abs(label.hashCode()) % AvatarColors.size] }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatarInitials(label),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = if (size >= 44.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
        )
    }
}
